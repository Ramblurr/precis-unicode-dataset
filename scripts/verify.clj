#!/usr/bin/env bb
(ns verify
  (:require
   [babashka.fs :as fs]
   [babashka.process :as p]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [common :as common]))

(def max-draft-unicode-version
  "Maximum Unicode version covered by the draft-nemoto-precis-unicode I-D document.
  Files for Unicode versions above this are expected to be missing from extracted tables
  since the reference draft only covers through Unicode 14.0."
  "14.0")

(def max-python-unicode-version
  "Maximum Unicode version covered by the Python precis_i18n library.
  Files for Unicode versions above this are expected to be missing from extracted tables
  since the Python library only covers through Unicode 16.0."
  "16.0")

(defn find-files
  "Find all .txt and .csv files in a directory using babashka.fs"
  [dir]
  (when (fs/exists? dir)
    (->> (concat (fs/glob dir "*.txt") (fs/glob dir "*.csv"))
         (map fs/file-name)
         set)))

(defn find-change-table-files
  "Find only change table files (changes-*-from-unassigned.txt and changes-*-property-changes.txt)"
  [dir]
  (when (fs/exists? dir)
    (->> (fs/glob dir "changes-*")
         (map fs/file-name)
         (filter #(or (str/ends-with? % "-from-unassigned.txt")
                      (str/ends-with? % "-property-changes.txt")))
         set)))

(defn unicode-version-from-filename
  "Extract Unicode version from filename, returns nil if not found.
  For change files like 'changes-14.0.0-15.0.0-from-unassigned.txt',
  extracts the target version (15.0.0)"
  [filename]
  (cond
    ;; Handle change files with version ranges (extract the "to" version)
    (re-find #"changes-(\d+\.\d+\.\d+)-(\d+\.\d+\.\d+)" filename)
    (when-let [match (re-find #"changes-\d+\.\d+\.\d+-(\d+)\.(\d+)" filename)]
      (str (nth match 1) "." (nth match 2)))

    ;; Handle regular version files (extract first version found)
    :else
    (when-let [match (re-find #"(\d+)\.(\d+)" filename)]
      (str (nth match 1) "." (nth match 2)))))

(defn file-beyond-draft-coverage?
  "Check if file is for Unicode version beyond draft I-D coverage"
  [filename]
  (when-let [version (unicode-version-from-filename filename)]
    (> (common/version-compare version max-draft-unicode-version) 0)))

(defn file-beyond-python-coverage?
  "Check if file is for Unicode version beyond Python library coverage"
  [filename]
  (when-let [version (unicode-version-from-filename filename)]
    (> (common/version-compare version max-python-unicode-version) 0)))

(defn run-diff
  "Run diff -w on two files, return true if identical"
  [file1 file2]
  (let [result (p/sh ["diff" "-w" file1 file2] {:continue true})]
    (zero? (:exit result))))

(defn check-file-differences
  "Check for differences in common files, return seq of mismatch pairs"
  [common-files]
  (->> common-files
       (map (fn [filename]
              (let [old-path (str common/extracted-dir "/" filename)
                    new-path (str common/generated-dir "/" filename)]
                (when-not (run-diff old-path new-path)
                  [old-path new-path]))))
       (filter some?)))

(defn get-diff-details
  "Get detailed diff information for two files"
  [file1 file2]
  (let [result        (p/sh ["diff" "-u" file1 file2] {:continue true})
        lines         (str/split-lines (:out result))
        ;; Filter to just the actual diff lines (starting with + or -)
        diff-lines    (->> lines
                           (drop-while #(not (str/starts-with? % "@@")))
                           (drop 1) ;; Skip the @@ line itself
                           (filter #(or (str/starts-with? % "-")
                                        (str/starts-with? % "+")))
                           (remove #(or (str/starts-with? % "---")
                                        (str/starts-with? % "+++"))))
        removed-count (count (filter #(str/starts-with? % "-") diff-lines))
        added-count   (count (filter #(str/starts-with? % "+") diff-lines))]
    {:removed    removed-count
     :added      added-count
     :diff-lines (take 20 diff-lines)}))

(defn verify-change-tables
  "Compare generated change table files against extracted reference files"
  []
  (let [old-files       (find-change-table-files common/extracted-dir)
        new-files       (find-change-table-files common/generated-dir)
        all-missing-old (clojure.set/difference new-files old-files)
        missing-old     (remove file-beyond-draft-coverage? all-missing-old)
        missing-new     (clojure.set/difference old-files new-files)
        common-files    (clojure.set/intersection old-files new-files)
        mismatches      (check-file-differences common-files)
        has-errors?     (or (seq missing-old) (seq missing-new) (seq mismatches))]

    (println "\n━━━ VALIDATE AGAINST draft-nemoto-precis-unicode14-00 CHANGE TABLES ━━━")
    (println (format "Extracted files: %d" (count old-files)))
    (println (format "Generated files: %d" (count new-files)))
    (println (format "Common files: %d" (count common-files)))
    (println (format "Files beyond draft coverage (ignored): %d" (count (filter file-beyond-draft-coverage? all-missing-old))))

    (when (seq missing-old)
      (println "\nGenerated files that have no extracted counterpart:")
      (doseq [file missing-old]
        (println (format "  %s" file))))

    (when (seq missing-new)
      (println "\nExtracted files that have no generated counterpart:")
      (doseq [file missing-new]
        (println (format "  %s" file))))

    (when (seq mismatches)
      (println "File differences summary")
      (doseq [[old-path new-path] mismatches]
        (let [filename     (fs/file-name old-path)
              diff-details (get-diff-details old-path new-path)]
          (println (format "\n▶ %s" filename))
          (println (format "  Lines removed from extracted: %d" (:removed diff-details)))
          (println (format "  Lines added in generated: %d" (:added diff-details)))
          (when (seq (:diff-lines diff-details))
            (println "\n  First differences (max 10 each):")
            (let [removed-lines (take 10 (filter #(str/starts-with? % "-") (:diff-lines diff-details)))
                  added-lines   (take 10 (filter #(str/starts-with? % "+") (:diff-lines diff-details)))]
              (when (seq removed-lines)
                (println "  Extracted (expected):")
                (doseq [line removed-lines]
                  (println (format "    %s" line))))
              (when (seq added-lines)
                (println "  Generated (actual):")
                (doseq [line added-lines]
                  (println (format "    %s" line)))))))))

    (if has-errors?
      (do
        (println "\nFAILED")
        false)
      (do
        (println "\nPASSED")
        true))))

(defn compare-codepoint
  "Pure function to compare a single codepoint between IANA and our mappings"
  [cp iana-mappings our-mappings]
  (let [iana-prop (get iana-mappings cp)
        our-prop  (get our-mappings cp)]
    {:cp        cp
     :iana-prop iana-prop
     :our-prop  our-prop
     :match?    (= iana-prop our-prop)}))

(defn compute-validation-stats
  "Pure function to compute validation statistics from comparison results"
  [comparison-results]
  (let [matches        (filter :match? comparison-results)
        mismatches     (remove :match? comparison-results)
        property-stats (->> matches
                            (map :iana-prop)
                            frequencies)]
    {:total-count      (count comparison-results)
     :match-count      (count matches)
     :mismatch-count   (count mismatches)
     :accuracy         (* 100.0 (/ (count matches) (count comparison-results)))
     :property-stats   property-stats
     :mismatch-details (vec mismatches)}))

(defn print-first-mismatches
  "Print first N mismatches for debugging"
  [mismatches limit]
  (doseq [{:keys [cp iana-prop our-prop]} (take limit mismatches)]
    (printf "MISMATCH at U+%04X: IANA=%s, Ours=%s%n" cp iana-prop our-prop)))

(defn print-iana-validation-report
  "Print IANA validation statistics and analysis"
  [{:keys [total-count match-count mismatch-count accuracy property-stats mismatch-details]}]
  (println (format "Total codepoints checked: %d" total-count))
  (println (format "Matches: %d" match-count))
  (println (format "Mismatches: %d" mismatch-count))
  (println (format "Accuracy: %.2f%%" accuracy))

  (println "\nProperty distribution:")
  (doseq [[prop count] (sort property-stats)]
    (println (format "  %s: %d" prop count)))

  (if (zero? mismatch-count)
    (println "\nPASSED")
    (do
      (println (format "\nFAILED (%d discrepancies)" mismatch-count))
      (print-first-mismatches mismatch-details 10)

      (let [by-iana-type (group-by :iana-prop mismatch-details)
            by-our-type  (group-by :our-prop mismatch-details)]

        (println "\nMismatch breakdown by IANA property:")
        (doseq [[prop cases] by-iana-type]
          (println (format "  IANA %s: %d cases" prop (count cases))))

        (println "\nMismatch breakdown by our property:")
        (doseq [[prop cases] by-our-type]
          (println (format "  Ours %s: %d cases" prop (count cases))))))))

(defn validate-against-iana-6-3-0
  "Validate our PRECIS algorithm against IANA Unicode 6.3.0 tables"
  []
  (let [iana-file (str common/extracted-dir "/precis-tables-6.3.0.csv")]

    (println "\n━━━ VALIDATE AGAINST IANA 6.3.0 REGISTRY ━━━")
    (if (.exists (io/file iana-file))
      (let [iana-data          (common/load-iana-csv iana-file)
            iana-mappings      (common/expand-iana-ranges-to-codepoints iana-data)
            mappings-file      (str common/generated-dir "/precis-mappings-6.3.0.edn")
            our-props-vec      (read-string (slurp mappings-file))
            our-mappings       (into {} (map-indexed (fn [cp [prop _reason]] [cp prop]) our-props-vec))
            all-cps            (set (concat (keys iana-mappings) (keys our-mappings)))
            comparison-results (map #(compare-codepoint % iana-mappings our-mappings) all-cps)
            stats              (compute-validation-stats comparison-results)]

        (print-iana-validation-report stats)
        (zero? (:mismatch-count stats)))

      (do
        (println (format "FAILED (IANA CSV file not found: %s)" iana-file))
        false))))

(defn verify-python-files
  "Compare generated Python derived-props files against extracted reference files"
  []
  (let [extracted-dir         common/extracted-dir
        generated-dir         common/generated-dir
        extracted-files       (->> (find-files extracted-dir)
                                   (filter #(str/starts-with? % "derived-props-"))
                                   (filter #(str/ends-with? % ".txt"))
                                   set)
        generated-files       (->> (find-files generated-dir)
                                   (filter #(str/starts-with? % "derived-props-"))
                                   (filter #(str/ends-with? % ".txt"))
                                   set)
        all-missing-extracted (set/difference generated-files extracted-files)
        missing-extracted     (->> all-missing-extracted
                                   (remove file-beyond-python-coverage?)
                                   ;; upstream does not have the 7.0 file
                                   (remove #(= % "derived-props-7.0.txt")))
        missing-generated     (set/difference extracted-files generated-files)
        common-files          (set/intersection extracted-files generated-files)
        mismatches            (check-file-differences common-files)
        has-errors?           (or (seq missing-extracted) (seq missing-generated) (seq mismatches))]

    (println "\n━━━ VALIDATE AGAINST PYTHON DERIVED-PROPS ━━━")

    (println (format "Extracted Python files: %d" (count extracted-files)))
    (println (format "Generated Python files: %d" (count generated-files)))
    (println (format "Common Python files: %d" (count common-files)))
    (println (format "Files beyond Python coverage (ignored): %d" (count (filter file-beyond-python-coverage? all-missing-extracted))))

    (when (seq missing-extracted)
      (println "\nGenerated Python files that have no extracted counterpart:")
      (doseq [file missing-extracted]
        (println (format "  %s" file))))

    (when (seq missing-generated)
      (println "\nExtracted Python files that have no generated counterpart:")
      (doseq [file missing-generated]
        (println (format "  %s" file))))

    (when (seq mismatches)
      (println "\nPython file differences found:")
      (doseq [[old-path new-path] mismatches]
        (println (format "  diff -w %s %s" old-path new-path))))

    (if has-errors?
      (do
        (println "\nFAILED")
        false)
      (do
        (println "\nPASSED")
        true))))

(defn -main [& _]

  (let [iana-result   (validate-against-iana-6-3-0)
        python-result (verify-python-files)
        tables-result (verify-change-tables)]

    (when-not (and iana-result python-result tables-result)
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
