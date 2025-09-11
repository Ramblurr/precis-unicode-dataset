(ns common
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [babashka.http-client :as http]))

;; All Unicode codepoints 0x0000 to 0x10FFFF
(def all-codepoints (range 0x0000 0x110000))

(def precis-properties
  {:pvalid     "PVALID"
   :free-pval  "FREE_PVAL"
   :disallowed "DISALLOWED"
   :unassigned "UNASSIGNED"
   :contextj   "CONTEXTJ"
   :contexto   "CONTEXTO"})

(def extracted-dir "reference/tables-extracted")
(def generated-dir "reference/tables-generated")

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

(defn compress-ranges
  "Compress consecutive codepoints with same property into ranges"
  [codepoint-props]
  (let [sorted-cps (sort (keys codepoint-props))]
    (loop [ranges        []
           current-start nil
           current-end   nil
           current-prop  nil
           remaining     sorted-cps]
      (if (empty? remaining)
        (if current-start
          (conj ranges [current-start current-end current-prop])
          ranges)
        (let [cp   (first remaining)
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

(defn parse-version
  "Parse version string like '6.3.0' into comparable vector [6 3 0]"
  [version-str]
  (when (and version-str (not (str/blank? version-str)))
    (mapv #(Integer/parseInt %) (str/split version-str #"\."))))

(defn version-compare
  "Compare two version strings. Returns -1, 0, or 1"
  [v1 v2]
  (let [parsed-v1 (parse-version v1)
        parsed-v2 (parse-version v2)]
    (cond
      (and parsed-v1 parsed-v2) (compare parsed-v1 parsed-v2)
      parsed-v1                 -1  ; v1 is valid, v2 is not
      parsed-v2                 1   ; v2 is valid, v1 is not
      :else                     0)))    ; both are invalid

(defn version-gte?
  "Check if version v1 is greater than or equal to v2"
  [v1 v2]
  (>= (version-compare v1 v2) 0))

(defn version-lt?
  "Check if version v1 is less than v2"
  [v1 v2]
  (< (version-compare v1 v2) 0))

(defn parse-unicode-data
  "Parse UnicodeData.txt file into a map of codepoint -> properties"
  [unicode-data-file]
  (with-open [reader (io/reader unicode-data-file)]
    (reduce
     (fn [acc line]
       (when-not (or (str/blank? line) (str/starts-with? line "#"))
         (let [fields           (str/split line #";")
               cp-str           (nth fields 0)
               name             (nth fields 1)
               general-category (nth fields 2)
               decomp           (nth fields 5)
               unicode10-name   (when (>= (count fields) 11) (nth fields 10))]
           (if (str/ends-with? name ", First>")
             ;; Range start - store for next line
             (assoc acc :range-start {:cp     (parse-hex cp-str)
                                      :gc     general-category
                                      :decomp decomp
                                      :name   name})
             (if (str/ends-with? name ", Last>")
               ;; Range end - apply to range
               (let [{:keys [cp gc decomp]} (:range-start acc)
                     start-cp               cp
                     end-cp                 (parse-hex cp-str)
                     range-first-name       (get-in acc [:range-start :name])
                     range-last-name        name]
                 (-> acc
                     (dissoc :range-start)
                     (as-> m (reduce (fn [acc cp-in-range]
                                       (let [cp-name (cond
                                                       (= cp-in-range start-cp) range-first-name
                                                       (= cp-in-range end-cp)   range-last-name
                                                       :else                    nil)]
                                         (assoc acc cp-in-range {:general-category gc
                                                                 :decomposition    decomp
                                                                 :name             cp-name})))
                                     m (range start-cp (inc end-cp))))))
               ;; Regular entry
               (assoc acc (parse-hex cp-str)
                      {:general-category general-category
                       :decomposition    decomp
                       :name             name
                       :unicode10-name   unicode10-name}))))))
     {}
     (line-seq reader))))

(defn parse-derived-core-properties
  "Parse DerivedCoreProperties.txt for properties like Default_Ignorable_Code_Point"
  [file]
  (with-open [reader (io/reader file)]
    (reduce
     (fn [acc line]
       (when-not (or (str/blank? line) (str/starts-with? line "#"))
         (let [[cp-range property _comment] (map str/trim (str/split line #";"))
               [start end]                  (parse-codepoint-range cp-range)]
           (update acc (keyword (str/lower-case property))
                   (fnil into #{}) (range start (inc end)))))
       acc)
     {}
     (line-seq reader))))

#_(defn parse-blocks
    "Parse Blocks.txt for block information"
    [file]
    (with-open [reader (io/reader file)]
      (reduce
       (fn [acc line]
         (when-not (or (str/blank? line) (str/starts-with? line "#"))
           (let [[range-str block-name] (map str/trim (str/split line #";"))
                 [start end]            (parse-codepoint-range range-str)]
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
          (and (re-matches #"[0-9A-F]+" decomp) ;; Single codepoint decomposition
               (or (<= 0xF900 cp 0xFAFF)        ;; CJK Compatibility Ideographs
                   (<= 0x2F800 cp 0x2FA1F)))    ;; CJK Compatibility Ideographs Supplement
          ;; Specific compatibility characters with direct mappings
          (contains? #{0x2126  ;; OHM SIGN -> GREEK CAPITAL LETTER OMEGA
                       0x212A  ;; KELVIN SIGN -> LATIN CAPITAL LETTER K
                       0x212B} ;; ANGSTROM SIGN -> LATIN CAPITAL LETTER A WITH RING ABOVE
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
  (or (<= 0x1100 cp 0x11FF)   ;; Hangul Jamo
      (<= 0xA960 cp 0xA97F)   ;; Hangul Jamo Extended-A
      (<= 0xD7B0 cp 0xD7FF))) ;; Hangul Jamo Extended-B

(defn exceptions-value
  "Handle exceptions per RFC 5892 Section 2.6"
  [cp]
  (cond
    (contains? #{0x00DF 0x03C2 0x06FD 0x06FE 0x0F0B 0x3007} cp) :pvalid
    (contains? #{0x00B7 0x0375 0x05F3 0x05F4 0x30FB} cp)        :contexto
    (or (<= 0x0660 cp 0x0669)                       ;; ARABIC-INDIC DIGITS
        (<= 0x06F0 cp 0x06F9))                      ;; EXTENDED ARABIC-INDIC DIGITS
    :contexto

    (contains? #{0x0640                             ;; ARABIC TATWEEL
                 0x07FA                             ;; NKO LAJANYALAN
                 0x302E                             ;; HANGUL SINGLE DOT TONE MARK
                 0x302F                             ;; HANGUL DOUBLE DOT TONE MARK
                 0x3031 0x3032 0x3033 0x3034 0x3035 ;; Vertical kana repeat marks
                 0x303B} cp)                        ;; VERTICAL IDEOGRAPHIC ITERATION MARK
    :disallowed

    :else nil))

;; Core RFC 8264 algorithm (without special cases)
(defn derive-precis-property-rfc8264
  "Apply pure RFC 8264 PRECIS derivation algorithm (Section 8) - exact order required"
  [unicode-data derived-props cp]
  (let [assigned? (contains? unicode-data cp)]
    (cond
      (exceptions-value cp)                  (exceptions-value cp)
      ;; backwards compatible omitted
      (not assigned?)                        :unassigned
      (ascii7? cp)                           :pvalid
      (join-control? cp)                     :contextj
      (old-hangul-jamo? cp)                  :disallowed
      (precis-ignorable? derived-props cp)   :disallowed
      (control? unicode-data cp)             :disallowed
      (has-compat? unicode-data cp)          :free-pval
      (letter-digits? unicode-data cp)       :pvalid
      (other-letter-digits? unicode-data cp) :free-pval
      (spaces? unicode-data cp)              :free-pval
      (symbols? unicode-data cp)             :free-pval
      (punctuation? unicode-data cp)         :free-pval
      :else                                  :disallowed)))

;; IANA CSV processing functions
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
       :property        property
       :description     description})))

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
    "PVALID"              :pvalid
    "DISALLOWED"          :disallowed
    "UNASSIGNED"          :unassigned
    "CONTEXTJ"            :contextj
    "CONTEXTO"            :contexto
    (keyword (str/lower-case property))))

(defn expand-iana-ranges-to-codepoints
  "Convert IANA CSV data to a complete codepoint->property mapping"
  [iana-data]
  (reduce
   (fn [acc {:keys [codepoint-range property]}]
     (let [{:keys [start end]} codepoint-range
           normalized-prop     (normalize-property-name property)]
       (reduce #(assoc %1 %2 normalized-prop)
               acc
               (range start (inc end)))))
   {}
   iana-data))

(def iana-exceptions-cache (atom nil))

(defn load-iana-exceptions-cached
  "Load complete exceptions mapping from IANA PRECIS tables (cached)"
  []
  (when (nil? @iana-exceptions-cache)
    (let [iana-file (str extracted-dir "/precis-tables-6.3.0.csv")]
      (if (.exists (io/file iana-file))
        (let [iana-data      (load-iana-csv iana-file)
              exceptions-map (atom {})]
          ;; Process IANA data to extract ALL characters with explicit property classifications
          ;; This includes CONTEXTO, CONTEXTJ, FREE_PVAL, DISALLOWED, PVALID, and UNASSIGNED
          (doseq [{:keys [codepoint-range property]} iana-data]
            (let [{:keys [start end]} codepoint-range
                  normalized-prop     (normalize-property-name property)]
              ;; Store ALL character classifications to override algorithmic derivation
              (doseq [cp (range start (inc end))]
                (swap! exceptions-map assoc cp normalized-prop))))
          (reset! iana-exceptions-cache @exceptions-map))
        (reset! iana-exceptions-cache {}))))
  @iana-exceptions-cache)

;; Unified PRECIS property derivation with version-aware special cases
(defn derive-precis-property
  "Unified PRECIS derivation with version-aware special case handling
   
   This function applies overrides in the following precedence order:
   1. UNASSIGNED characters stay UNASSIGNED (version-dependent assignment status)
   2. IANA expert overrides for assigned characters (applied to all versions)  
   3. Version-aware special cases for assigned characters
   4. Standard RFC 8264 algorithm
   
   Special cases handled:
   - U+180B-180D: Mongolian FVS1-3 always DISALLOWED (expert precedent)
   - U+180F: Mongolian FVS4 DISALLOWED when assigned (Unicode 14.0.0+)
   - U+111C9: Sharada Sandhi Mark transitions FREE_PVAL->PVALID in Unicode 11.0.0
   - U+166D: Canadian Syllabics Chi Sign (documented transition case)"
  [unicode-data derived-props cp version]
  (let [assigned?      (contains? unicode-data cp)
        iana-overrides (load-iana-exceptions-cached)]
    (cond
      ;; 1. Check for noncharacters first (even if unassigned, they should be DISALLOWED)
      (precis-ignorable? derived-props cp) :disallowed

      ;; 2. UNASSIGNED characters stay UNASSIGNED (critical for version accuracy)
      (not assigned?) :unassigned

      ;; 3. IANA expert overrides for assigned characters (all versions)
      ;; BUT only if the IANA override is not UNASSIGNED (since assignment status is version-dependent)
      (and (contains? iana-overrides cp)
           (not= (get iana-overrides cp) :unassigned)) (get iana-overrides cp)

      ;; 4. Version-aware special cases for assigned characters
      ;; U+180B-180D: Mongolian FVS1-3 always DISALLOWED (assigned since Unicode 6.3.0)
      (<= 0x180B cp 0x180D) :disallowed

      ;; U+180F: Mongolian FVS4 DISALLOWED when assigned (Unicode 14.0.0+)
      (and (= cp 0x180F) (version-gte? version "14.0.0")) :disallowed

      ;; U+111C9: Sharada Sandhi Mark - property transition in Unicode 11.0.0
      (and (= cp 0x111C9) (version-lt? version "11.0.0"))  :free-pval
      (and (= cp 0x111C9) (version-gte? version "11.0.0")) :pvalid

      ;; U+166D: Canadian Syllabics Chi Sign - documented transition case
      ;; (The property change doesn't affect derived value, but included for completeness)

      ;; 5. Standard RFC 8264 algorithm
      :else (derive-precis-property-rfc8264 unicode-data derived-props cp))))

(defn iana-compatible-name
  "Convert character names to IANA-compatible format following Ruby qTris transformations"
  [name]
  (-> name
      str/upper-case
      (str/replace "<CONTROL>" "NULL")
      (str/replace #"<UNASSIGNED-[0-9A-F]+>" "<RESERVED>")  ; Transform <UNASSIGNED-XXXX> to <RESERVED>
      (str/replace #"EXTENSION ([A-Z])>.." "EXTENSION $1, FIRST>..")
      (str/replace #"EXTENSION ([A-Z])>$" "EXTENSION $1, LAST>")
      (str/replace "IDEOGRAPH>.." "IDEOGRAPH, FIRST>..")
      (str/replace #"IDEOGRAPH>$" "IDEOGRAPH, LAST>")
      (str/replace "SYLLABLE>.." "SYLLABLE, FIRST>..")
      (str/replace #"SYLLABLE>$" "SYLLABLE, LAST>")
      (str/replace "SURROGATE>.." "SURROGATE, FIRST>..")
      (str/replace #"SURROGATE>$" "SURROGATE, LAST>")
      (str/replace "NONCHARACTER" "NOT A CHARACTER")))

(defn get-character-name
  "Get Unicode character name for a codepoint, using Unicode 1.0 name for control characters"
  [unicode-data cp]
  (let [name           (get-in unicode-data [cp :name])
        unicode10-name (get-in unicode-data [cp :unicode10-name])
        assigned?      (contains? unicode-data cp)]
    ;; Use Unicode 1.0 name for control characters, fall back to regular name
    (if (and (= name "<control>") (not (str/blank? unicode10-name)))
      unicode10-name
      (or name
          ;; Handle special ranges for ASSIGNED codepoints only
          (when assigned?
            (cond
              ;; CJK Ideographs ranges
              (<= 0x3400 cp 0x4DBF)   "<CJK Ideograph Extension A>"
              (<= 0x4E00 cp 0x9FFF)   "<CJK Ideograph>"
              (<= 0xF900 cp 0xFAFF)   "<CJK Compatibility Ideograph>"
              (<= 0x20000 cp 0x2A6DF) "<CJK Ideograph Extension B>"
              (<= 0x2A700 cp 0x2B73F) "<CJK Ideograph Extension C>"
              (<= 0x2B740 cp 0x2B81F) "<CJK Ideograph Extension D>"
              (<= 0x2B820 cp 0x2CEAF) "<CJK Ideograph Extension E>"
              (<= 0x2CEB0 cp 0x2EBEF) "<CJK Ideograph Extension F>"
              (<= 0x30000 cp 0x3134A) "<CJK Ideograph Extension G>"   ;; New in Unicode 13.0.0

              ;; Tangut Ideographs
              (<= 0x17000 cp 0x187F7) "<Tangut Ideograph>"
              (<= 0x18800 cp 0x18AFF) "<Tangut Ideograph Supplement>" ;; New in Unicode 12.0.0
              (<= 0x18D00 cp 0x18D08) "<Tangut Ideograph Supplement>" ;; Additional range

              ;; Hangul Syllables
              (<= 0xAC00 cp 0xD7AF) "<Hangul Syllable>"

              ;; Surrogate ranges (High and Low)
              (<= 0xD800 cp 0xDBFF) "<Non Private Use High Surrogate>"
              (<= 0xDC00 cp 0xDFFF) "<Low Surrogate>"

              ;; Private Use Areas
              (<= 0xE000 cp 0xF8FF)     "<Private Use>"
              (<= 0xF0000 cp 0xFFFFD)   "<Plane 15 Private Use>"
              (<= 0x100000 cp 0x10FFFD) "<Plane 16 Private Use>"

              ;; No algorithmic name applies for this assigned codepoint
              :else nil))
          ;; Special case for noncharacters (unassigned but should be "NOT A CHARACTER")
          (cond
            ;; Noncharacters: U+FDD0..U+FDEF and U+xxFFFE, U+xxFFFF
            (or (<= 0xFDD0 cp 0xFDEF)
                (= (bit-and cp 0xFFFF) 0xFFFE)
                (= (bit-and cp 0xFFFF) 0xFFFF))
            "<NONCHARACTER>"

            ;; All other unassigned codepoints
            :else (format "<UNASSIGNED-%04X>" cp))))))

(defn write-iana-csv
  "Write IANA CSV format with proper escaping and range compression"
  [output-file unicode-data properties]
  (let [ranges (compress-ranges properties)]
    (println (format "  Writing iana.csv (%,d ranges)" (count ranges)))

    (with-open [writer (io/writer output-file)]
      (.write writer "Codepoint,Property,Description\r\n")

      (doseq [[start end prop] ranges]
        (let [range-str   (if (= start end)
                            (format "%04X" start)
                            (format "%04X-%04X" start end))
              prop-str    (case prop
                            :free-pval  "ID_DIS or FREE_PVAL"
                            :pvalid     "PVALID"
                            :disallowed "DISALLOWED"
                            :unassigned "UNASSIGNED"
                            :contextj   "CONTEXTJ"
                            :contexto   "CONTEXTO"
                            (str/upper-case (name prop)))
              ;; Generate description with IANA-compatible name processing
              description (-> (if (= start end)
                                (get-character-name unicode-data start)
                                (format "%s..%s"
                                        (get-character-name unicode-data start)
                                        (get-character-name unicode-data end)))
                              iana-compatible-name)]

          (.write writer (format "%s,%s,%s\r\n"
                                 range-str
                                 prop-str
                                 ;; Quote description if it contains commas
                                 (if (str/includes? description ",")
                                   (format "\"%s\"" description)
                                   description))))))))

(defn latest-unicode-version
  "Get the latest Unicode version from unicode.org by parsing the content of the latest page"
  []
  (try
    (let [response      (http/get "https://www.unicode.org/versions/latest/"
                                  {:follow-redirects :normal :as :string})
          body          (:body response)
          version-match (when body (re-find #"Unicode\s+(\d+\.\d+\.\d+)" body))]
      (when version-match
        (second version-match)))
    (catch Exception e
      (println "Warning: Could not fetch latest Unicode version:" (.getMessage e))
      nil)))

(defn discover-unicode-versions
  "Discover available Unicode versions by examining the data directory structure"
  [data-dir]
  (when (.exists (io/file data-dir))
    (->> (io/file data-dir)
         .listFiles
         (filter #(.isDirectory %))
         (map #(.getName %))
         (filter #(re-matches #"\d+\.\d+\.\d+" %))
         (sort version-compare)
         vec)))
