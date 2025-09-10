#!/usr/bin/env bb
(ns verify)

(require
 '[generate :refer [load-iana-csv expand-iana-ranges-to-codepoints generate-complete-precis-mappings]]
 '[babashka.process :as p]
 '[clojure.java.io :as io]
 '[clojure.string :as str]
 '[clojure.set :as set])

(def tables-dir "tables-extracted")
(def tables-new-dir "tables-generated")
(def unicode-base-path "createtables")
(def scratch-dir "scratch")
(def unicode-versions ["6.3.0" "7.0.0" "8.0.0" "9.0.0" "10.0.0" "11.0.0" "12.0.0" "13.0.0" "14.0.0" "15.0.0" "16.0.0" "17.0.0"])

(defn find-files
  "Find all .txt and .csv files in a directory"
  [dir]
  (when (.exists (io/file dir))
    (->> (file-seq (io/file dir))
         (filter #(.isFile %))
         (filter #(or (str/ends-with? (.getName %) ".txt")
                      (str/ends-with? (.getName %) ".csv")))
         (map #(.getName %))
         set)))

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
              (let [old-path (str tables-dir "/" filename)
                    new-path (str tables-new-dir "/" filename)]
                (when-not (run-diff old-path new-path)
                  [old-path new-path]))))
       (filter some?)))

(defn verify-tables
  "Compare all table files between directories"
  []
  (let [old-files (find-files tables-dir)
        new-files (find-files tables-new-dir)
        missing-old (clojure.set/difference new-files old-files)
        missing-new (clojure.set/difference old-files new-files)
        common-files (clojure.set/intersection old-files new-files)
        mismatches (check-file-differences common-files)
        has-errors? (or (seq missing-old) (seq missing-new) (seq mismatches))]

    ;; Report missing files
    (when (seq missing-old)
      (println "ERROR: Generated files that have no extracted counterpart:")
      (doseq [file missing-old]
        (println " " file)))

    (when (seq missing-new)
      (println "ERROR: Extracted files that have no generated counterpart:")
      (doseq [file missing-new]
        (println " " file)))

    ;; Report file differences
    (when (seq mismatches)
      (println "ERROR: File differences found:")
      (doseq [[old-path new-path] mismatches]
        (println (format "diff -w %s %s" old-path new-path))))

    ;; Final result
    (if has-errors?
      (System/exit 1)
      (println "All files match"))))

(defn validate-against-iana-6-3-0
  "Validate our PRECIS algorithm against IANA Unicode 6.3.0 tables"
  []
  (let [iana-file (str scratch-dir "/precis-tables-6.3.0.csv")]
    (if (.exists (io/file iana-file))
      (do
        (println (format "Loading IANA PRECIS tables from: %s" iana-file))
        (let [iana-data (load-iana-csv iana-file)
              iana-mappings (expand-iana-ranges-to-codepoints iana-data)

              mappings-file (str tables-new-dir "/precis-mappings-6.3.0.edn")
              our-mappings (read-string (slurp mappings-file))

              all-cps (set (concat (keys iana-mappings) (keys our-mappings)))

              matches (atom 0)
              mismatches (atom 0)
              property-stats (atom {})
              mismatch-details (atom [])]

          (println (format "Comparing %d codepoints..." (count all-cps)))

          (doseq [cp all-cps]
            (let [iana-prop (get iana-mappings cp)
                  our-prop (get our-mappings cp)]
              (if (= iana-prop our-prop)
                (do
                  (swap! matches inc)
                  (swap! property-stats update iana-prop (fnil inc 0)))
                (do
                  (swap! mismatches inc)
                  (swap! mismatch-details conj {:cp cp :iana iana-prop :ours our-prop})
                  (when (< @mismatches 10) ; Show first 10 mismatches
                    (printf "MISMATCH at U+%04X: IANA=%s, Ours=%s%n"
                            cp iana-prop our-prop))))))

          (println "\n=== IANA VALIDATION RESULTS ===")
          (println (format "Total codepoints checked: %d" (count all-cps)))
          (println (format "Matches: %d" @matches))
          (println (format "Mismatches: %d" @mismatches))
          (println (format "Accuracy: %.2f%%" (* 100.0 (/ @matches (count all-cps)))))

          (println "\nProperty distribution:")
          (doseq [[prop count] (sort @property-stats)]
            (println (format "  %s: %d" prop count)))

          (if (zero? @mismatches)
            (println "\n✅ PERFECT MATCH! Our PRECIS algorithm is 100% accurate.")
            (do
              (println (format "\n❌ Found %d discrepancies. Algorithm needs debugging." @mismatches))

              ;; Analyze mismatches by type
              (let [by-iana-type (group-by :iana @mismatch-details)
                    by-our-type (group-by :ours @mismatch-details)]

                (println "\nMismatch breakdown by IANA property:")
                (doseq [[prop cases] by-iana-type]
                  (println (format "  IANA %s: %d cases" prop (count cases))))

                (println "\nMismatch breakdown by our property:")
                (doseq [[prop cases] by-our-type]
                  (println (format "  Ours %s: %d cases" prop (count cases)))))))

          (= 0 @mismatches)))

      (do
        (println (format "❌ IANA CSV file not found or download failed: %s" iana-file))
        false))))

(defn -main [& args]
  (validate-against-iana-6-3-0)
  (verify-tables))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
