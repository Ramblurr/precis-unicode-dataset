#!/usr/bin/env bb
(ns report
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [common :as common]))

;; Data structures
(defrecord VersionChange [from-version to-version stats notes discrepancies])
(defrecord PropertyStats [pvalid unassigned contextj contexto disallowed free-pval total])
(defrecord PropertyDelta [before after change])

;; Configuration
(def data-dir "data")
(def get-unicode-versions (memoize #(common/discover-unicode-versions data-dir)))

(def tables-dir "reference/tables-generated")
(def extracted-dir "reference/tables-extracted")
(def output-file "REPORT.md")

(defn parse-property-line
  "Parse a single line from a table file"
  [line]
  (when-not (str/blank? line)
    (let [parts (str/split line #";")
          cp-range (str/trim (first parts))
          property-section (when (> (count parts) 1) (second parts))
          property (when property-section
                     (str/trim (first (str/split property-section #"#"))))]
      (when (and cp-range property)
        {:cp-range (common/parse-codepoint-range cp-range)
         :property (keyword (str/lower-case (str/replace property #"_" "-")))}))))

(defn read-table-file
  "Read and parse a table file, returning a sequence of property entries"
  [filepath]
  (if (.exists (io/file filepath))
    (->> (slurp filepath)
         str/split-lines
         (map parse-property-line)
         (filter some?))
    []))

(defn expand-ranges
  "Expand range entries to individual codepoints with properties"
  [entries]
  (reduce (fn [acc {:keys [cp-range property]}]
            (let [[start end] cp-range]
              (reduce #(assoc %1 %2 property)
                      acc
                      (range start (inc end)))))
          {}
          entries))

(defn normalize-property
  "Normalize property names for consistency"
  [prop]
  (case prop
    (:id-dis :free-pval :id-dis-or-free-pval) :free-pval
    prop))

(defn count-properties
  "Count occurrences of each property in a mapping"
  [property-map]
  (reduce (fn [acc [_ prop]]
            (update acc (normalize-property prop) (fnil inc 0)))
          {}
          property-map))

(defn calculate-property-stats
  "Calculate complete property statistics for all codepoints"
  [property-map]
  (let [counts (count-properties property-map)]
    (->PropertyStats
     (get counts :pvalid 0)
     (get counts :unassigned 0)
     (get counts :contextj 0)
     (get counts :contexto 0)
     (get counts :disallowed 0)
     (get counts :free-pval 0)
     1114112))) ; Total Unicode codepoints

(defn load-iana-baseline
  "Load IANA 6.3.0 baseline for comparison"
  []
  (let [iana-file (str extracted-dir "/precis-tables-6.3.0.csv")]
    (if (.exists (io/file iana-file))
      (with-open [reader (io/reader iana-file)]
        (->> (line-seq reader)
             (drop 1) ; Skip header
             (map (fn [line]
                    (let [[codepoint property _] (str/split line #",")]
                      (when (and codepoint property)
                        (let [range (common/parse-codepoint-range-iana codepoint)
                              cp-range [(:start range) (:end range)]]
                          {:cp-range cp-range
                           :property (normalize-property
                                      (keyword (str/lower-case
                                                (str/replace property #"[ _]" "-"))))})))))
             (filter some?)
             expand-ranges))
      {})))

(defn process-version-change
  "Process changes between two Unicode versions"
  [from-version to-version baseline-props]
  (let [from-unassigned-file (str tables-dir "/changes-" from-version "-" to-version "-from-unassigned.txt")
        property-changes-file (str tables-dir "/changes-" from-version "-" to-version "-property-changes.txt")

        ;; Read the change files
        from-unassigned (read-table-file from-unassigned-file)
        property-changes (read-table-file property-changes-file)

        ;; Calculate how many codepoints changed
        unassigned-changes (expand-ranges from-unassigned)
        prop-changes (expand-ranges property-changes)

        ;; For stats, we need to track the cumulative state
        ;; Start with baseline and apply changes progressively
        current-props (atom baseline-props)

        ;; Apply changes from this version transition
        _ (doseq [[cp prop] unassigned-changes]
            (swap! current-props assoc cp prop))
        _ (doseq [[cp prop] prop-changes]
            (swap! current-props assoc cp prop))

        ;; Calculate stats after changes
        after-stats (calculate-property-stats @current-props)

        ;; Count of changes from non-UNASSIGNED properties
        non-unassigned-changes (count prop-changes)]

    {:from-version from-version
     :to-version to-version
     :stats after-stats
     :non-unassigned-changes non-unassigned-changes
     :updated-props @current-props}))

(defn format-delta
  "Format a property delta for display"
  [before after property-name]
  (let [change (- after before)]
    (cond
      (zero? change) (format "%s did not change, at %,d" property-name after)
      (pos? change) (format "%s changed from %,d to %,d (+%,d)" property-name before after change)
      :else (format "%s changed from %,d to %,d (%,d)" property-name before after change))))

(defn generate-version-notes
  "Generate version-specific notes based on known discrepancies and changes"
  [from-version to-version non-unassigned-changes]
  (let [has-impact (> non-unassigned-changes 0)
        base-statement (if has-impact
                         (format "There are changes made to Unicode between version %s and %s that impact PRECIS calculation of the derived property values."
                                 from-version to-version)
                         (format "There are no changes made to Unicode between version %s and %s that impact PRECIS calculation of the derived property values."
                                 from-version to-version))]
    (concat
     [base-statement]
     (cond
       ;; 6.3.0 -> 7.0.0
       (and (= from-version "6.3.0") (= to-version "7.0.0"))
       [""
        "Note: PRECIS Derived Property Value of the character ARABIC LETTER BEH WITH HAMZA ABOVE (U+08A1) added in Unicode 7.0.0 is PVALID in this review."]

       ;; 10.0.0 -> 11.0.0
       (and (= from-version "10.0.0") (= to-version "11.0.0"))
       [""
        "Change of SHARADA SANDHI MARK (U+111C9) added in Unicode 8.0.0 affects PRECIS calculation of the derived property values in IdentifierClass."
        "PRECIS Derived Property Value of this between Unicode 8.0.0 and Unicode 10.0.0 is ID_DIS or FREE_PVAL, however in Unicode 11.0.0 is PVALID."]

       ;; 11.0.0 -> 12.0.0
       (and (= from-version "11.0.0") (= to-version "12.0.0"))
       [""
        "Unicode General Properties of CANADIAN SYLLABICS CHI SIGN (U+166D) was changed from Po to So in Unicode 12.0.0."
        "This change has changed the basis for calculating of the derived property value from Punctuation (P) to Symbols (O)."
        "However, this change does not affect the calculation result."]

       ;; 13.0.0 -> 14.0.0
       (and (= from-version "13.0.0") (= to-version "14.0.0"))
       [""
        "MONGOLIAN FREE VARIATION SELECTOR FOUR (U+180F) transitions from UNASSIGNED to DISALLOWED."]

       ;; Default - no additional notes
       :else
       []))))

(defn format-version-section
  "Format a complete version change section for the report"
  [from-version to-version before-stats after-stats non-unassigned-changes]
  (let [notes (generate-version-notes from-version to-version non-unassigned-changes)]
    (str/join "\n"
              (concat
               [(format "### Changes between Unicode %s and %s" from-version to-version)
                ""
                "Change in number of characters in each category:"
                ""
                (str "- " (format-delta (:pvalid before-stats) (:pvalid after-stats) "PVALID"))
                (str "- " (format-delta (:unassigned before-stats) (:unassigned after-stats) "UNASSIGNED"))
                (str "- " (format-delta (:contextj before-stats) (:contextj after-stats) "CONTEXTJ"))
                (str "- " (format-delta (:contexto before-stats) (:contexto after-stats) "CONTEXTO"))
                (str "- " (format-delta (:disallowed before-stats) (:disallowed after-stats) "DISALLOWED"))
                (str "- " (format-delta (:free-pval before-stats) (:free-pval after-stats) "ID_DIS or FREE_PVAL"))
                (str "- " (format-delta (:total before-stats) (:total after-stats) "TOTAL"))
                ""
                (format "Code points that changed derived property value from other than UNASSIGNED: %d"
                        non-unassigned-changes)]
               (when (seq notes)
                 (concat [""] notes))
               [""]))))

(defn update-readme-with-report
  "Update README.md by embedding the report between the markers"
  [report-content]
  (let [readme-file "README.md"
        start-marker "<!-- START of REPORT.md embed -->"
        end-marker "<!-- END of REPORT.md embed -->"]
    (if (.exists (io/file readme-file))
      (let [readme-content (slurp readme-file)
            start-idx (str/index-of readme-content start-marker)
            end-idx (str/index-of readme-content end-marker)]
        (if (and start-idx end-idx)
          (let [before (subs readme-content 0 (+ start-idx (count start-marker)))
                after (subs readme-content end-idx)
                new-content (str before "\n" report-content "\n" after)]
            (spit readme-file new-content)
            (println "README.md updated with report content"))
          (println "Warning: Could not find markers in README.md")))
      (println "Warning: README.md not found"))))

(defn generate-report
  "Generate the complete PRECIS Unicode compatibility report"
  [update-readme?]
  (println "Generating PRECIS Unicode Compatibility Report...")

  ;; Load baseline IANA 6.3.0 data
  (let [baseline-props (load-iana-baseline)
        baseline-stats (calculate-property-stats baseline-props)

        ;; Process each version transition
        unicode-versions (get-unicode-versions)
        version-pairs (partition 2 1 unicode-versions)

        ;; Accumulate changes through versions
        report-sections (reduce
                         (fn [{:keys [current-props current-stats sections]} [from-ver to-ver]]
                           (let [result (process-version-change from-ver to-ver current-props)
                                 section (format-version-section
                                          from-ver to-ver
                                          current-stats
                                          (:stats result)
                                          (:non-unassigned-changes result))]
                             {:current-props (:updated-props result)
                              :current-stats (:stats result)
                              :sections (conj sections section)}))
                         {:current-props baseline-props
                          :current-stats baseline-stats
                          :sections []}
                         version-pairs)

        ;; Get the latest Unicode version
        latest-version (last unicode-versions)

        ;; Generate complete report
        report-content (str/join "\n"
                                 [(format "## Overview of Changes Between Unicode 6.3.0 and %s" latest-version)
                                  ""
                                  "This section describes the differences in the Derived Property Values for each Unicode Major Version to consider whether the PRECIS framework can follow the Unicode standard's updates since Unicode 6.3.0."
                                  ""
                                  (str/join "\n" (:sections report-sections))])]

    ;; Write report to file
    (spit output-file report-content)
    (println (format "Report generated: %s" output-file))

    ;; Update README if requested
    (when update-readme?
      (update-readme-with-report report-content))))

;; Main execution
(defn -main [& args]
  (let [update-readme? (some #{"--readme"} args)]
    (generate-report update-readme?)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
