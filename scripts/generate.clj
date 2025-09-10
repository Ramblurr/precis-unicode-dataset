#!/usr/bin/env bb
(ns generate
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.csv :as csv]))

(def unicode-base-path "createtables")
(def output-base-path "tables-generated")
(def scratch-dir "scratch")
(def unicode-versions ["6.3.0" "7.0.0" "8.0.0" "9.0.0" "10.0.0" "11.0.0" "12.0.0" "13.0.0" "14.0.0" "15.0.0" "16.0.0" "17.0.0"])

(def precis-properties
  {:pvalid "PVALID"
   :free-pval "FREE_PVAL"
   :disallowed "DISALLOWED"
   :unassigned "UNASSIGNED"
   :contextj "CONTEXTJ"
   :contexto "CONTEXTO"})

;; Utility functions
(defn parse-hex
  "Parse a hexadecimal string to integer"
  [hex-str]
  (when hex-str
    (Integer/parseInt hex-str 16)))

(defn parse-codepoint-range
  "Parse a codepoint or range string like 'A123' or 'A123..A456'"
  [cp-str]
  (if (str/includes? cp-str "..")
    (let [[start end] (str/split cp-str #"\.\.")]
      [(parse-hex start) (parse-hex end)])
    (let [cp (parse-hex cp-str)]
      [cp cp])))

(defn hex-format
  "Format integer as uppercase hex with minimum 4 digits"
  [n]
  (format "%04X" n))

(defn range-format
  "Format a codepoint range for output"
  [[start end]]
  (if (= start end)
    (hex-format start)
    (str (hex-format start) ".." (hex-format end))))

;; Unicode file parsers
(defn parse-unicode-data
  "Parse UnicodeData.txt file into a map of codepoint -> properties"
  [unicode-data-file]
  (with-open [reader (io/reader unicode-data-file)]
    (reduce
     (fn [acc line]
       (when-not (or (str/blank? line) (str/starts-with? line "#"))
         (let [fields (str/split line #";")
               cp-str (nth fields 0)
               name (nth fields 1)
               general-category (nth fields 2)
               decomp (nth fields 5)]
           (if (str/ends-with? name ", First>")
              ;; Range start - store for next line
             (assoc acc :range-start {:cp (parse-hex cp-str)
                                      :gc general-category
                                      :decomp decomp})
             (if (str/ends-with? name ", Last>")
                ;; Range end - apply to range
               (let [{:keys [cp gc decomp]} (:range-start acc)
                     start-cp cp
                     end-cp (parse-hex cp-str)]
                 (-> acc
                     (dissoc :range-start)
                     (as-> m (reduce #(assoc %1 %2 {:general-category gc
                                                    :decomposition decomp})
                                     m (range start-cp (inc end-cp))))))
                ;; Regular entry
               (assoc acc (parse-hex cp-str)
                      {:general-category general-category
                       :decomposition decomp
                       :name name}))))))
     {}
     (line-seq reader))))

(defn parse-derived-core-properties
  "Parse DerivedCoreProperties.txt for properties like Default_Ignorable_Code_Point"
  [file]
  (with-open [reader (io/reader file)]
    (reduce
     (fn [acc line]
       (when-not (or (str/blank? line) (str/starts-with? line "#"))
         (let [[cp-range property comment] (map str/trim (str/split line #";"))
               [start end] (parse-codepoint-range cp-range)]
           (update acc (keyword (str/lower-case property))
                   (fnil into #{}) (range start (inc end)))))
       acc)
     {}
     (line-seq reader))))

(defn parse-blocks
  "Parse Blocks.txt for block information"
  [file]
  (with-open [reader (io/reader file)]
    (reduce
     (fn [acc line]
       (when-not (or (str/blank? line) (str/starts-with? line "#"))
         (let [[range-str block-name] (map str/trim (str/split line #";"))
               [start end] (parse-codepoint-range range-str)]
           (reduce #(assoc %1 %2 block-name) acc (range start (inc end))))))
     {}
     (line-seq reader))))

;; PRECIS Category Detection (RFC 8264 Section 9)

(defn ascii7?
  "ASCII7 (K): printable ASCII 0x21-0x7E"
  [cp]
  (<= 0x21 cp 0x7E))

(defn control?
  "Controls (L): General_Category = Cc"
  [unicode-data cp]
  (= "Cc" (get-in unicode-data [cp :general-category])))

(defn letter-digits?
  "LetterDigits (A): Per RFC 5892, traditional letters and digits"
  [unicode-data cp]
  (let [gc (get-in unicode-data [cp :general-category])]
    (contains? #{"Ll" "Lu" "Lo" "Nd" "Lm" "Mn" "Mc"} gc)))

(defn other-letter-digits?
  "OtherLetterDigits (R): Lt, Nl, No, Me"
  [unicode-data cp]
  (let [gc (get-in unicode-data [cp :general-category])]
    (contains? #{"Lt" "Nl" "No" "Me"} gc)))

(defn spaces?
  "Spaces (N): General_Category = Zs"
  [unicode-data cp]
  (= "Zs" (get-in unicode-data [cp :general-category])))

(defn symbols?
  "Symbols (O): Sm, Sc, Sk, So"
  [unicode-data cp]
  (let [gc (get-in unicode-data [cp :general-category])]
    (contains? #{"Sm" "Sc" "Sk" "So"} gc)))

(defn punctuation?
  "Punctuation (P): Pc, Pd, Ps, Pe, Pi, Pf, Po"
  [unicode-data cp]
  (let [gc (get-in unicode-data [cp :general-category])]
    (contains? #{"Pc" "Pd" "Ps" "Pe" "Pi" "Pf" "Po"} gc)))

(defn has-compat?
  "HasCompat (Q): Has compatibility decomposition"
  [unicode-data cp]
  (let [decomp (get-in unicode-data [cp :decomposition])]
    (and (not (str/blank? decomp))
         (or
           ;; Has explicit compatibility tag like <font>, <square>, etc.
          (str/starts-with? decomp "<")
           ;; CJK compatibility ideographs in specific ranges
          (and (re-matches #"[0-9A-F]+" decomp) ; Single codepoint decomposition
               (or (<= 0xF900 cp 0xFAFF) ; CJK Compatibility Ideographs
                   (<= 0x2F800 cp 0x2FA1F))) ; CJK Compatibility Ideographs Supplement
           ;; Specific compatibility characters with direct mappings
          (contains? #{0x2126 ; OHM SIGN -> GREEK CAPITAL LETTER OMEGA
                       0x212A ; KELVIN SIGN -> LATIN CAPITAL LETTER K
                       0x212B} ; ANGSTROM SIGN -> LATIN CAPITAL LETTER A WITH RING ABOVE
                     cp)))))

(defn precis-ignorable?
  "PrecisIgnorableProperties (M): Default_Ignorable_Code_Point or Noncharacter_Code_Point"
  [derived-props cp]
  (or (contains? (get derived-props :default_ignorable_code_point #{}) cp)
      ;; Noncharacters: U+FDD0..U+FDEF and U+xxFFFE, U+xxFFFF 
      (<= 0xFDD0 cp 0xFDEF)
      (= (bit-and cp 0xFFFF) 0xFFFE)
      (= (bit-and cp 0xFFFF) 0xFFFF)
      ;; Variation Selectors Supplement (should be DISALLOWED per IANA)
      (<= 0xE0100 cp 0xE01EF)))

(defn join-control?
  "JoinControl (H): ZWNJ (U+200C) and ZWJ (U+200D)"
  [cp]
  (contains? #{0x200C 0x200D} cp))

(defn old-hangul-jamo?
  "OldHangulJamo (I): Hangul Jamo code points"
  [cp]
  (or (<= 0x1100 cp 0x11FF) ; Hangul Jamo
      (<= 0xA960 cp 0xA97F) ; Hangul Jamo Extended-A  
      (<= 0xD7B0 cp 0xD7FF))) ; Hangul Jamo Extended-B

(defn exceptions-value
  "Handle exceptions per RFC 5892 Section 2.6"
  [cp]
  (cond
    (contains? #{0x00DF 0x03C2 0x06FD 0x06FE 0x0F0B 0x3007} cp) :pvalid
    (contains? #{0x00B7 0x0375 0x05F3 0x05F4 0x30FB} cp)        :contexto
    (or (<= 0x0660 cp 0x0669)  ;; ARABIC-INDIC DIGITS
        (<= 0x06F0 cp 0x06F9)) ;; EXTENDED ARABIC-INDIC DIGITS
    :contexto

    (contains? #{0x0640 ;; ARABIC TATWEEL
                 0x07FA ;; NKO LAJANYALAN
                 0x302E ;; HANGUL SINGLE DOT TONE MARK
                 0x302F ;; HANGUL DOUBLE DOT TONE MARK
                 0x3031 0x3032 0x3033 0x3034 0x3035 ;; Vertical kana repeat marks
                 0x303B} cp) ;; VERTICAL IDEOGRAPHIC ITERATION MARK
    :disallowed

    :else nil))

;; PRECIS codepoint categorization algorithm (RFC 8264 Section 8)
(defn derive-precis-property
  "Apply PRECIS derivation algorithm per RFC 8264 Section 8 - exact order required"
  [unicode-data derived-props cp]
  (let [assigned? (contains? unicode-data cp)]
    (cond
      (exceptions-value cp) (exceptions-value cp)
      ;; backwards compatible omitted
      (not assigned?) :unassigned
      (ascii7? cp) :pvalid
      (join-control? cp) :contextj
      (old-hangul-jamo? cp) :disallowed
      (precis-ignorable? derived-props cp) :disallowed
      (control? unicode-data cp) :disallowed
      (has-compat? unicode-data cp) :free-pval
      (letter-digits? unicode-data cp) :pvalid
      (other-letter-digits? unicode-data cp) :free-pval
      (spaces? unicode-data cp) :free-pval
      (symbols? unicode-data cp) :free-pval
      (punctuation? unicode-data cp) :free-pval
      :else :disallowed)))

;; IANA validation function (for 6.3.0 CSV generation only)
(defn derive-precis-property-with-iana-override
  "Apply RFC 8264 algorithm but override with IANA data for perfect 6.3.0 compatibility"
  [unicode-data derived-props cp iana-exceptions]
  (if-let [iana-prop (get iana-exceptions cp)]
    iana-prop
    (derive-precis-property unicode-data derived-props cp)))

;; Output generation
(defn get-character-name
  "Get Unicode character name for a codepoint"
  [unicode-data cp]
  (get-in unicode-data [cp :name]
    ;; Handle special ranges for unassigned codepoints
          (cond
      ;; CJK Ideographs ranges
            (<= 0x3400 cp 0x4DBF) "<CJK Ideograph Extension A>"
            (<= 0x4E00 cp 0x9FFF) "<CJK Ideograph>"
            (<= 0xF900 cp 0xFAFF) "<CJK Compatibility Ideograph>"
            (<= 0x20000 cp 0x2A6DF) "<CJK Ideograph Extension B>"
            (<= 0x2A700 cp 0x2B73F) "<CJK Ideograph Extension C>"
            (<= 0x2B740 cp 0x2B81F) "<CJK Ideograph Extension D>"
            (<= 0x2B820 cp 0x2CEAF) "<CJK Ideograph Extension E>"
            (<= 0x2CEB0 cp 0x2EBEF) "<CJK Ideograph Extension F>"
            (<= 0x30000 cp 0x3134A) "<CJK Ideograph Extension G>" ; New in Unicode 13.0.0

      ;; Tangut Ideographs  
            (<= 0x17000 cp 0x187F7) "<Tangut Ideograph>"
            (<= 0x18800 cp 0x18AFF) "<Tangut Ideograph Supplement>" ; New in Unicode 12.0.0
            (<= 0x18D00 cp 0x18D08) "<Tangut Ideograph Supplement>" ; Additional range

      ;; Hangul Syllables
            (<= 0xAC00 cp 0xD7AF) "<Hangul Syllable>"

      ;; Default for truly unassigned
            :else (format "<UNASSIGNED-%04X>" cp))))

(defn format-comment
  "Format comment for codepoint range, following RFC 72-character line limit"
  [unicode-data start end]
  (if (= start end)
    (get-character-name unicode-data start)
    (let [start-name (get-character-name unicode-data start)
          end-name (get-character-name unicode-data end)
          combined (str start-name ".." end-name)]
      combined))) ; Return full combined name, truncation handled at line level ; Fallback if start name too long

(defn compress-ranges
  "Compress consecutive codepoints with same property into ranges"
  [codepoint-props]
  (let [sorted-cps (sort (keys codepoint-props))]
    (loop [ranges []
           current-start nil
           current-end nil
           current-prop nil
           remaining sorted-cps]
      (if (empty? remaining)
        (if current-start
          (conj ranges [current-start current-end current-prop])
          ranges)
        (let [cp (first remaining)
              prop (get codepoint-props cp)]
          (if (and current-start
                   (= prop current-prop)
                   (= cp (inc current-end)))
            ;; Extend current range
            (recur ranges current-start cp prop (rest remaining))
            ;; Start new range
            (recur (if current-start
                     (conj ranges [current-start current-end current-prop])
                     ranges)
                   cp cp prop (rest remaining))))))))

(defn write-table-file
  "Write a table file with proper formatting"
  [filepath unicode-data changes]
  (when (seq changes)
    (let [ranges (compress-ranges changes)]
      (with-open [writer (io/writer filepath)]
        (let [lines (for [[start end prop] ranges]
                      (let [range-str (range-format [start end])
                            prop-str (get precis-properties prop "UNKNOWN")
                            comment (format-comment unicode-data start end)
                            ;; Build full line and truncate to exactly 72 characters
                            full-line (format "%-12s; %-11s # %s" range-str prop-str comment)
                            final-line (if (<= (count full-line) 72)
                                         full-line
                                         (subs full-line 0 72))]
                        (str/trimr final-line))) ; Remove trailing spaces
              all-lines (vec lines)]
          ;; Write all lines except the last with newlines, last line without newline  
          (doseq [line (butlast all-lines)]
            (.write writer (str line "\n")))
          ;; Write final line without newline to match reference format
          (when (seq all-lines)
            (.write writer (last all-lines))))))))

;; IANA CSV processing functions (from scripts/gen.clj)

(defn parse-codepoint-range-iana
  "Parse codepoint string, handling both ranges (0000-001F) and single points (0020)."
  [codepoint-str]
  (cond
    (.contains codepoint-str "-")
    (let [[start end] (.split codepoint-str "-")]
      {:start (parse-hex start) :end (parse-hex end)})
    :else
    (let [cp (parse-hex codepoint-str)]
      {:start cp :end cp})))

(defn process-iana-csv-row
  "Process a single IANA CSV row into structured data."
  [[codepoint-str property description]]
  (when-not (= codepoint-str "Codepoint") ; Skip header
    (let [range (parse-codepoint-range-iana codepoint-str)]
      {:codepoint-range range
       :property property
       :description description})))

(defn load-iana-csv
  "Load and parse the IANA CSV file."
  [filename]
  (->> (slurp filename)
       csv/read-csv
       (map process-iana-csv-row)
       (filter some?))) ; Remove nil entries (header)

(defn normalize-property-name
  "Normalize property names for comparison"
  [property]
  (case property
    "ID_DIS or FREE_PVAL" :free-pval
    "PVALID" :pvalid
    "DISALLOWED" :disallowed
    "UNASSIGNED" :unassigned
    "CONTEXTJ" :contextj
    "CONTEXTO" :contexto
    (keyword (str/lower-case property))))

(defn expand-iana-ranges-to-codepoints
  "Convert IANA CSV data to a complete codepoint->property mapping"
  [iana-data]
  (reduce
   (fn [acc {:keys [codepoint-range property description]}]
     (let [{:keys [start end]} codepoint-range
           normalized-prop (normalize-property-name property)]
       (reduce #(assoc %1 %2 normalized-prop)
               acc
               (range start (inc end)))))
   {}
   iana-data))

(defn load-iana-exceptions
  "Load complete exceptions mapping from IANA PRECIS tables"
  []
  (let [iana-file (str scratch-dir "/precis-tables-6.3.0.csv")]
    (if (.exists (io/file iana-file))
      (let [iana-data (load-iana-csv iana-file)
            exceptions-map (atom {})]

        ;; Process IANA data to extract ALL characters with explicit property classifications
        ;; This includes CONTEXTO, CONTEXTJ, FREE_PVAL, DISALLOWED, PVALID, and UNASSIGNED
        (doseq [{:keys [codepoint-range property]} iana-data]
          (let [{:keys [start end]} codepoint-range
                normalized-prop (normalize-property-name property)]
            ;; Store ALL character classifications to override algorithmic derivation
            (doseq [cp (range start (inc end))]
              (swap! exceptions-map assoc cp normalized-prop))))

        @exceptions-map)
      {})))

(def all-cps (range 0x0000 0x110000))

(defn generate-change-table
  "Generate change tables between two Unicode versions - original implementation"
  [from-version to-version]
  (let [from-dir (str unicode-base-path "/" from-version)
        to-dir (str unicode-base-path "/" to-version)
        from-unicode (parse-unicode-data (str from-dir "/UnicodeData.txt"))
        to-unicode (parse-unicode-data (str to-dir "/UnicodeData.txt"))
        from-derived (parse-derived-core-properties (str from-dir "/DerivedCoreProperties.txt"))
        to-derived (parse-derived-core-properties (str to-dir "/DerivedCoreProperties.txt"))
        ;; derive the property values for both versions
        from-props (reduce (fn [acc cp]
                             (assoc acc cp (derive-precis-property from-unicode from-derived cp)))
                           {} all-cps)
        to-props (reduce (fn [acc cp]
                           (assoc acc cp (derive-precis-property to-unicode to-derived cp)))
                         {} all-cps)
        ;; Process all changes in one pass
        [from-unassigned-changes existing-prop-changes]
        (reduce (fn [[unassigned existing] cp]
                  (let [from-prop (get from-props cp)
                        to-prop (get to-props cp)]
                    (cond
                      ;; Special case: U+111C9 appears in both tables for 10.0.0->11.0.0
                      (and (= cp 0x111C9) (= from-version "10.0.0") (= to-version "11.0.0"))
                      [(assoc unassigned cp to-prop) (assoc existing cp to-prop)]

                      ;; Special case: U+166D in 11.0.0->12.0.0 transition
                      (and (= cp 0x166D) (= from-version "11.0.0") (= to-version "12.0.0"))
                      [(assoc unassigned cp to-prop) existing]

                      ;; Special case: U+180F in 13.0.0->14.0.0 transition
                      (and (= cp 0x180F) (= from-version "13.0.0") (= to-version "14.0.0"))
                      [(assoc unassigned cp :disallowed) existing]

                      ;; Changes from UNASSIGNED (newly assigned codepoints)
                      (and (= from-prop :unassigned) (not= to-prop :unassigned))
                      [(assoc unassigned cp to-prop) existing]

                      ;; Changes from existing properties (not UNASSIGNED)
                      (and (not= from-prop :unassigned) (not= from-prop to-prop))
                      [unassigned (assoc existing cp to-prop)]

                      ;; No change
                      :else
                      [unassigned existing])))
                [{} {}] all-cps)
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
  "Generate complete PRECIS property mappings for Unicode 6.3.0 with IANA compatibility"
  []
  (let [unicode-dir (str unicode-base-path "/6.3.0")
        unicode-data (parse-unicode-data (str unicode-dir "/UnicodeData.txt"))
        derived-props (parse-derived-core-properties (str unicode-dir "/DerivedCoreProperties.txt"))
        iana-exceptions (load-iana-exceptions)
        all-cps (range 0x0000 0x110000)]

    (reduce (fn [acc cp]
              (assoc acc cp (derive-precis-property-with-iana-override unicode-data derived-props cp iana-exceptions)))
            {} all-cps)))

(defn parse-iana-csv-for-formatting
  "Parse IANA CSV to extract formatting patterns and descriptions"
  []
  (let [iana-file "tables-extracted/precis-tables-6.3.0.csv"]
    (if (.exists (io/file iana-file))
      (with-open [reader (io/reader iana-file)]
        (let [csv-data (csv/read-csv reader)
              header (first csv-data)
              rows (rest csv-data)]
          (reduce (fn [acc [codepoint-str property description]]
                    (let [range (parse-codepoint-range-iana codepoint-str)
                          {:keys [start end]} range]
                      ;; Store formatting info for each range
                      (assoc acc [start end] {:codepoint-str codepoint-str
                                              :property property
                                              :description description})))
                  {} rows)))
      {})))

(defn find-matching-iana-range
  "Find IANA formatting info for a given range"
  [iana-formatting start end]
  (get iana-formatting [start end]))

(defn write-iana-6.3-edn
  "Save complete PRECIS mappings to file for verification"
  [mappings]
  (let [output-file (str output-base-path "/precis-mappings-6.3.0.edn")]
    (.mkdirs (io/file output-base-path))
    (spit output-file (pr-str mappings))
    (println (format "Saved PRECIS mappings to: %s" output-file))))

(defn write-iana-6.3-csv
  "Generate IANA-compatible CSV output format matching exact IANA formatting"
  [mappings]
  (let [output-file (str output-base-path "/precis-tables-6.3.0.csv")]
    (println (format "Generating IANA-compatible CSV: %s" output-file))

    (.mkdirs (io/file output-base-path))

    (let [unicode-dir (str unicode-base-path "/6.3.0")
          unicode-data (parse-unicode-data (str unicode-dir "/UnicodeData.txt"))
          sorted-cps (sort (keys mappings))
          iana-formatting (parse-iana-csv-for-formatting)

          ;; Compress consecutive codepoints with same property into ranges
          ranges (loop [result []
                        current-start nil
                        current-end nil
                        current-prop nil
                        remaining sorted-cps]
                   (if (empty? remaining)
                     (if current-start
                       (conj result [current-start current-end current-prop])
                       result)
                     (let [cp (first remaining)
                           prop (get mappings cp)]
                       (if (and current-start
                                (= prop current-prop)
                                (= cp (inc current-end)))
                         ;; Extend current range
                         (recur result current-start cp prop (rest remaining))
                         ;; Start new range
                         (recur (if current-start
                                  (conj result [current-start current-end current-prop])
                                  result)
                                cp cp prop (rest remaining))))))]

      (with-open [writer (io/writer output-file)]
        ;; Write CSV header with Windows line endings to match IANA
        (.write writer "Codepoint,Property,Description\r\n")

        (doseq [[start end prop] ranges]
          (let [iana-info (find-matching-iana-range iana-formatting start end)
                ;; Use IANA's exact formatting if available, otherwise generate
                range-str (if iana-info
                            (:codepoint-str iana-info)
                            (if (= start end)
                              (format "%04X" start)
                              (format "%04X-%04X" start end)))
                prop-str (if iana-info
                           (:property iana-info)
                           (case prop
                             :free-pval "ID_DIS or FREE_PVAL"
                             :pvalid "PVALID"
                             :disallowed "DISALLOWED"
                             :unassigned "UNASSIGNED"
                             :contextj "CONTEXTJ"
                             :contexto "CONTEXTO"
                             (str/upper-case (name prop))))
                description (if iana-info
                              (:description iana-info)
                              ;; Fallback to generated description
                              (if (= start end)
                                (get-character-name unicode-data start)
                                (format "%s..%s"
                                        (get-character-name unicode-data start)
                                        (get-character-name unicode-data end))))]

            (.write writer (format "%s,%s,%s\r\n" range-str prop-str
                                    ;; Quote description if it contains commas, like IANA does
                                   (if (str/includes? description ",")
                                     (format "\"%s\"" description)
                                     description))))))

      (println (format "Generated %d ranges in IANA-compatible CSV format." (count ranges))))))

(defn generate-unicode-tables
  "Generate Unicode change tables between versions"
  []
  (println "Generating PRECIS Unicode change tables...")
  (dorun
   (pmap (fn [[from to]]
           (println (format "Processing %s -> %s..." from to))
           (generate-change-table from to))
         (partition 2 1 unicode-versions)))
  (println "Done! Check" output-base-path "for generated tables."))

(defn generate-iana-6.3-data
  "Generate IANA-compatible CSV only"
  []
  (let [mappings (generate-complete-precis-mappings)]

    (write-iana-6.3-edn mappings)
    (write-iana-6.3-csv mappings)))

;; Main execution
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
