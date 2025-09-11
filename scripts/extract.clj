#!/usr/bin/env bb
(ns extract
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn parse-version-from-header
  "Extract version numbers from section headers like 'Changes from Unicode 6.3.0 to Unicode 7.0.0'"
  [header]
  (when-let [matches (re-seq #"Unicode (\d+\.\d+\.\d+)" header)]
    (let [versions (map second matches)]
      (when (= 2 (count versions))
        (str (first versions) "-" (second versions))))))

(defn determine-table-type
  "Determine the type of table based on preceding context"
  [lines-before]
  (let [context (str/join " " (take-last 15 lines-before))]
    (cond
      (str/includes? context "Changes from derived property value UNASSIGNED") "from-unassigned"
      ;; (str/includes? context "Changes from derived property value ID_DIS or FREE_PVAL to PVALID") "id-dis-to-pvalid"
      (str/includes? context "Changes from derived property value")            "property-changes"
      :else                                                                    "from-unassigned"))) ; Default to from-unassigned since that's most common

(defn extract-tables
  "Extract tables from the markdown file and write them to separate .txt files"
  [input-file output-dir]
  (println "Reading" input-file)
  (let [content (slurp input-file)
        lines   (str/split-lines content)]

    (println "Creating output directory:" output-dir)
    (.mkdirs (io/file output-dir))

    (loop [lines            lines
           current-section  nil
           in-table         false
           table-lines      []
           table-counter    0
           all-lines-so-far []
           skip-tables      false] ; New flag to skip tables in UCD sections
      (when-let [line (first lines)]
        (cond
          ;; Found a new section header for changes
          (str/starts-with? line "# Changes from Unicode")
          (do
            ;; Write previous table if we had one
            (when (and current-section (seq table-lines))
              (let [table-type (determine-table-type all-lines-so-far)
                    filename   (str "changes-" current-section "-" table-type ".txt")
                    filepath   (str output-dir "/" filename)]
                (println "Writing" filepath)
                (spit filepath (str/join "\n" table-lines))))
            ;; Start tracking new section - reset skip flag
            (let [version-range (parse-version-from-header line)]
              ;; (println "Found section:" line "-> version range:" version-range)
              (recur (rest lines) version-range false [] 0 (conj all-lines-so-far line) false)))

          ;; Found a UCD format section - set skip flag
          (str/starts-with? line "# Code points in Unicode Character Database")
          (do
            ;; (println "Found UCD section, skipping tables:" line)
            ;; Write any pending table before switching to skip mode
            (when (and current-section (seq table-lines))
              (let [table-type (determine-table-type all-lines-so-far)
                    filename   (str "changes-" current-section "-" table-type ".txt")
                    filepath   (str output-dir "/" filename)]
                (println "Writing" filepath)
                (spit filepath (str/join "\n" table-lines))))
            (recur (rest lines) current-section false [] table-counter (conj all-lines-so-far line) true))

          ;; Found start of table (~~~~ fence)
          (and current-section (str/starts-with? line "~~~~") (not in-table) (not skip-tables))
          (recur (rest lines) current-section true [] table-counter (conj all-lines-so-far line) skip-tables)

          ;; Found end of table (~~~~ fence) - only process if not skipping
          (and current-section in-table (str/starts-with? line "~~~~"))
          (if skip-tables
            (do
              (println "Skipping table in UCD section with" (count table-lines) "lines")
              (recur (rest lines) current-section false [] table-counter (conj all-lines-so-far line) skip-tables))
            (do
              ;; Write the table immediately with appropriate suffix
              (let [table-type (determine-table-type all-lines-so-far)
                    filename   (str "changes-" current-section "-" table-type ".txt")
                    filepath   (str output-dir "/" filename)]
                (spit filepath (str/join "\n" table-lines)))
              ;; Continue processing in same section for additional tables
              (recur (rest lines) current-section false [] (inc table-counter) (conj all-lines-so-far line) skip-tables)))

          ;; Collect table content - only if not skipping
          (and current-section in-table (not (str/blank? line)) (not skip-tables))
          (recur (rest lines) current-section in-table (conj table-lines line) table-counter (conj all-lines-so-far line) skip-tables)

          ;; Skip table content if in skip mode
          (and current-section in-table skip-tables)
          (recur (rest lines) current-section in-table [] table-counter (conj all-lines-so-far line) skip-tables)

          ;; Skip other lines
          :else
          (recur (rest lines) current-section in-table table-lines table-counter (conj all-lines-so-far line) skip-tables))))

    (println "Extraction complete!")))

(defn -main [& _]
  (let [input-file "reference/tables-extracted/draft-nemoto-precis-unicode.md"
        output-dir "reference/tables-extracted"]

    ;; Check if IANA CSV exists (should be downloaded by download.clj)
    (let [iana-file (str output-dir "/precis-tables-6.3.0.csv")]
      (when-not (.exists (io/file iana-file))
        (println "Warning: IANA CSV not found at" iana-file)
        (println "Run 'bb download' first to download reference files")))

    ;; Extract tables from the markdown file if it exists
    (if (.exists (io/file input-file))
      (extract-tables input-file output-dir)
      (do
        (println "Error: Input file" input-file "not found")
        (println "Run 'bb download' first to download the draft document")
        (System/exit 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
