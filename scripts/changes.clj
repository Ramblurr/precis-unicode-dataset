#!/usr/bin/env bb
(ns changes
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [common :as common :refer [all-codepoints]]))

(def unicode-base-path "data")
(def output-base-path "reference/tables-generated")

(defn format-comment
  "Format comment for codepoint range, following RFC 72-character line limit"
  [unicode-data start end]
  (if (= start end)
    (common/get-character-name unicode-data start)
    (let [start-name (common/get-character-name unicode-data start)
          end-name   (common/get-character-name unicode-data end)
          combined   (str start-name ".." end-name)]
      combined)))

(defn write-table-file
  "Write a table file with proper formatting"
  [filepath unicode-data changes]
  (when (seq changes)
    (let [ranges (common/compress-ranges changes)]
      (with-open [writer (io/writer filepath)]
        (let [lines     (for [[start end prop] ranges]
                          (let [range-str  (common/range-format [start end])
                                prop-str   (get common/precis-properties prop "UNKNOWN")
                                comment    (format-comment unicode-data start end)
                                ;; Build full line and truncate to exactly 72 characters
                                full-line  (format "%-12s; %-11s # %s" range-str prop-str comment)
                                final-line (if (<= (count full-line) 72)
                                             full-line
                                             (subs full-line 0 72))]
                            (str/trimr final-line)))
              all-lines (vec lines)]
          ;; Write all lines except the last with newlines, last line without newline  
          (doseq [line (butlast all-lines)]
            (.write writer (str line "\n")))
          ;; Write final line without newline to match reference format
          (when (seq all-lines)
            (.write writer (last all-lines))))))))

(defn generate-change-table
  "Generate change tables between two Unicode versions using unified derive-precis-property"
  [from-version to-version]
  (let [from-dir     (str unicode-base-path "/" from-version)
        to-dir       (str unicode-base-path "/" to-version)
        from-unicode (common/parse-unicode-data (str from-dir "/UnicodeData.txt"))
        to-unicode   (common/parse-unicode-data (str to-dir "/UnicodeData.txt"))
        from-derived (common/parse-derived-core-properties (str from-dir "/DerivedCoreProperties.txt"))
        to-derived   (common/parse-derived-core-properties (str to-dir "/DerivedCoreProperties.txt"))
        ;; derive the property values for both versions using unified function
        from-props   (reduce (fn [acc cp]
                               (assoc acc cp (common/derive-precis-property from-unicode from-derived cp from-version)))
                             {} all-codepoints)
        to-props     (reduce (fn [acc cp]
                               (assoc acc cp (common/derive-precis-property to-unicode to-derived cp to-version)))
                             {} all-codepoints)
        ;; Process all changes in one pass
        [from-unassigned-changes existing-prop-changes]
        (reduce (fn [[unassigned existing] cp]
                  (let [from-prop (get from-props cp)
                        to-prop   (get to-props cp)]
                    (cond
                      ;; Special case: U+111C9 appears in both tables for 10.0.0->11.0.0
                      (and (= cp 0x111C9) (= from-version "10.0.0") (= to-version "11.0.0"))
                      [(assoc unassigned cp to-prop) (assoc existing cp to-prop)]

                      ;; Special case: U+166D in 11.0.0->12.0.0 transition
                      (and (= cp 0x166D) (= from-version "11.0.0") (= to-version "12.0.0"))
                      [(assoc unassigned cp to-prop) existing]

                      ;; Changes from UNASSIGNED (newly assigned codepoints)
                      (and (= from-prop :unassigned) (not= to-prop :unassigned))
                      [(assoc unassigned cp to-prop) existing]

                      ;; Changes from existing properties (not UNASSIGNED)
                      (and (not= from-prop :unassigned) (not= from-prop to-prop))
                      [unassigned (assoc existing cp to-prop)]

                      ;; No change
                      :else
                      [unassigned existing])))
                [{} {}] all-codepoints)
        base-filename (str output-base-path "/changes-" from-version "-" to-version)]
    (.mkdirs (io/file output-base-path))
    (when (seq from-unassigned-changes)
      (write-table-file (str base-filename "-from-unassigned.txt")
                        to-unicode from-unassigned-changes))
    (when (seq existing-prop-changes)
      (let [suffix "-property-changes"]
        (write-table-file (str base-filename suffix ".txt")
                          to-unicode existing-prop-changes)))))

(defn generate-complete-precis-mappings
  "Generate complete PRECIS property mappings for Unicode 6.3.0 (uses IANA overrides automatically)"
  []
  (let [unicode-dir   (str unicode-base-path "/6.3.0")
        unicode-data  (common/parse-unicode-data (str unicode-dir "/UnicodeData.txt"))
        derived-props (common/parse-derived-core-properties (str unicode-dir "/DerivedCoreProperties.txt"))
        all-cps       (range 0x0000 0x110000)]

    (reduce (fn [acc cp]
              ;; Uses unified function which automatically applies IANA overrides for 6.3.0
              (assoc acc cp (common/derive-precis-property unicode-data derived-props cp "6.3.0")))
            {} all-cps)))

(defn write-iana-6.3-edn
  "Save complete PRECIS mappings to file for verification"
  [mappings]
  (let [output-file (str output-base-path "/precis-mappings-6.3.0.edn")]
    (.mkdirs (io/file output-base-path))
    (spit output-file (pr-str mappings))))

(defn write-iana-6.3-csv
  "Generate IANA-compatible CSV output format matching exact IANA formatting"
  [mappings]
  (let [output-file (str output-base-path "/precis-tables-6.3.0.csv")]
    (println (format "Generating IANA-compatible CSV: %s" output-file))
    (.mkdirs (io/file output-base-path))
    (let [unicode-dir  (str unicode-base-path "/6.3.0")
          unicode-data (common/parse-unicode-data (str unicode-dir "/UnicodeData.txt"))]
      (common/write-iana-csv output-file unicode-data mappings))))

(defn generate-unicode-tables
  "Generate Unicode change tables between versions"
  []
  (let [unicode-versions (common/discover-unicode-versions unicode-base-path)]
    (if (seq unicode-versions)
      (do
        (println "Generating PRECIS Unicode change tables...")
        (dorun
         (pmap (fn [[from to]]
                 (println (format "Processing %s -> %s..." from to))
                 (generate-change-table from to))
               (partition 2 1 unicode-versions)))
        (println "Done! Check" output-base-path "for generated tables.")
        (println "or to verify correctness run: bb verify"))
      (println "No Unicode versions found in" unicode-base-path))))

(defn generate-iana-6.3-data
  "Generate IANA-compatible CSV only"
  []
  (let [mappings (generate-complete-precis-mappings)]

    (write-iana-6.3-edn mappings)
    (write-iana-6.3-csv mappings)))

(defn -main [& args]
  (cond
    (some #{"--iana"} args)
    (generate-iana-6.3-data)

    (some #{"--unicode"} args)
    (generate-unicode-tables)

    :else
    (do
      (generate-iana-6.3-data)
      (generate-unicode-tables))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
