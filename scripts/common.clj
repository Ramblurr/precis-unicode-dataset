(ns common
  (:require
   [babashka.http-client :as http]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]))

;; All Unicode codepoints 0x0000 to 0x10FFFF
(def all-codepoints (range 0x0000 0x110000))

(def precis-properties
  {:pvalid     "PVALID"
   :free-pval  "FREE_PVAL"
   :disallowed "DISALLOWED"
   :unassigned "UNASSIGNED"
   :contextj   "CONTEXTJ"
   :contexto   "CONTEXTO"})

(def unicode-base-path "data")
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

(defn compress-ranges-vec
  "Compress consecutive codepoints with same property into ranges
  Input: vector where index=codepoint, value=[derived-property-value reason]
  Output: vector of [start end [derived-property-value reason]]"
  [props-vec & {:keys [with-reason?]}]
  (let [ranges-t (transient [])]
    (loop [current-start nil
           current-end   nil
           current-prop  nil
           cp            0]
      (if (>= cp (count props-vec))
        (do
          (when current-start
            (conj! ranges-t [current-start current-end current-prop]))
          (persistent! ranges-t))
        (let [prop-tuple  (nth props-vec cp)
              compare-val (if with-reason? prop-tuple (first prop-tuple))]
          (if (and current-start
                   (= compare-val (if with-reason? current-prop (first current-prop)))
                   (= cp (inc current-end)))
            ;; Extend current range
            (recur current-start cp prop-tuple (inc cp))
            ;; Start new range or add previous range
            (do
              (when current-start
                (conj! ranges-t [current-start current-end current-prop]))
              (recur cp cp prop-tuple (inc cp)))))))))

(defn compress-ranges-map
  "Compress consecutive codepoints with same property into ranges

  Input: map of codepoints -> [derived-property-value reason]
  Output: vector of [start end [derived-property-value reason]]"
  [codepoint-props & {:keys [with-reason?]}]
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
        (let [cp          (first remaining)
              prop-tuple  (get codepoint-props cp)
              compare-val (if with-reason? prop-tuple (first prop-tuple))]
          (if (and current-start
                   (= compare-val (if with-reason? current-prop (first current-prop)))
                   (= cp (inc current-end)))
            ;; Extend current range
            (recur ranges current-start cp prop-tuple (rest remaining))
            ;; Start new range
            (recur (if current-start
                     (conj ranges [current-start current-end current-prop])
                     ranges)
                   cp cp prop-tuple (rest remaining))))))))

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
      parsed-v1                 -1   ;; v1 is valid, v2 is not
      parsed-v2                 1    ;; v2 is valid, v1 is not
      :else                     0))) ;; both are invalid

(defn version-gte?
  "Check if version v1 is greater than or equal to v2"
  [v1 v2]
  (>= (version-compare v1 v2) 0))

(defn version-lt?
  "Check if version v1 is less than v2"
  [v1 v2]
  (< (version-compare v1 v2) 0))

(defn version-to-short-format
  "Convert version from X.Y.Z format to X.Y format for filename generation"
  [version]
  (let [parts (str/split version #"\.")]
    (str (first parts) "." (second parts))))

(defn python-filename-format [version]
  (str "derived-props-" (version-to-short-format version) ".txt"))

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
       (if (or (str/blank? line) (str/starts-with? line "#"))
         acc
         (let [[cp-range property-with-comment] (map str/trim (str/split line #";" 2))
               property                         (-> property-with-comment
                                                    (str/split #"#" 2)
                                                    first
                                                    str/trim)
               [start end]                      (parse-codepoint-range cp-range)]
           (update acc (keyword (str/lower-case property))
                   (fnil into #{}) (range start (inc end))))))
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
  "HasCompat (Q): toNFKC(cp) != cp"
  [cp]
  (let [char (cond
               (<= cp 0xFFFF) (str (char cp))
               :else          (String. (int-array [cp]) 0 1))
        nfkc (java.text.Normalizer/normalize char java.text.Normalizer$Form/NFKC)]
    (not= char nfkc)))

(defn non-character?
  "Noncharacters are code points that are permanently reserved in the Unicode Standard for internal use.
  Defined by https://www.unicode.org/versions/Unicode17.0.0/core-spec/chapter-23/#G12612"
  [cp]
  (or (<= 0xFDD0 cp 0xFDEF)
      (= (bit-and cp 0xFFFF) 0xFFFE)
      (= (bit-and cp 0xFFFF) 0xFFFF)))

(defn precis-ignorable?
  "PrecisIgnorableProperties (M): Default_Ignorable_Code_Point or Noncharacter_Code_Point"
  [derived-props cp]
  (or (contains? (get derived-props :default_ignorable_code_point #{}) cp)
      (non-character? cp)
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

(defn backwards-compatible? [_cp]
  false)

(defn unassigned?
  "Unassigned (J): General_Category(cp) is in {Cn} and Noncharacter_Code_Point(cp) = False
   Per RFC 5892 Section 2.10"
  [unicode-data cp]
  (let [assigned? (contains? unicode-data cp)]
    (and (not assigned?)  ;; General_Category = "Cn" (implicit when not in Unicode data)
         (not (non-character? cp)))))

(defn derive-precis-property
  "Apply pure RFC 8264 PRECIS derivation algorithm as per Section 8

  Returns a tuple of [property-value reaason]
  "
  [unicode-data derived-props cp]
  (cond
    ;; the order of these cond expressions are critical
    (exceptions-value cp)                  [(exceptions-value cp) :exceptions]
    (backwards-compatible? cp)             [(backwards-compatible? cp) :backwards-compatible]
    (unassigned? unicode-data cp)          [:unassigned :unassigned]
    (ascii7? cp)                           [:pvalid :ascii7]
    (join-control? cp)                     [:contextj :join-control]
    (old-hangul-jamo? cp)                  [:disallowed :old-hangul-jamo]
    (precis-ignorable? derived-props cp)   [:disallowed :precis-ignorable-properties]
    (control? unicode-data cp)             [:disallowed :controls]
    (has-compat? cp)                       [:free-pval :has-compat]
    (letter-digits? unicode-data cp)       [:pvalid :letter-digits]
    (other-letter-digits? unicode-data cp) [:free-pval :other-letter-digits]
    (spaces? unicode-data cp)              [:free-pval :spaces]
    (symbols? unicode-data cp)             [:free-pval :symbols]
    (punctuation? unicode-data cp)         [:free-pval :punctuation]
    :else                                  [:disallowed :other]))

(defn build-props-vector
  "Build a vector of PRECIS properties for all codepoints using transients for performance.
    Returns a vector where index = codepoint, value = [property reason] tuple"
  [unicode-data derived-props]
  (let [size (long 0x110000)]
    (loop [^long cp 0
           v        (transient (vec (repeat size nil)))]
      (if (< cp size)
        (recur (unchecked-inc cp)
               (assoc! v cp (common/derive-precis-property unicode-data derived-props cp)))
        (persistent! v)))))

(defn build-version-properties [version]
  (let [unicode-dir   (str unicode-base-path "/" version)
        unicode-data  (parse-unicode-data (str unicode-dir "/UnicodeData.txt"))
        derived-props (parse-derived-core-properties (str unicode-dir "/DerivedCoreProperties.txt"))
        props-vec     (build-props-vector unicode-data derived-props)]
    [version {:props props-vec :unicode-data unicode-data :version version}]))

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
  (when-not (= codepoint-str "Codepoint")
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
       (filter some?)))

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

(defn iana-compatible-name
  "Convert character names to IANA-compatible format following Ruby qTris transformations"
  [name]
  (-> name
      str/upper-case
      (str/replace "<CONTROL>" "NULL")
      (str/replace #"<UNASSIGNED-[0-9A-F]+>" "<RESERVED>")
      (str/replace #"EXTENSION ([A-Z])>.." "EXTENSION $1, FIRST>..")
      (str/replace #"EXTENSION ([A-Z])>$" "EXTENSION $1, LAST>")
      (str/replace "IDEOGRAPH>.." "IDEOGRAPH, FIRST>..")
      (str/replace #"IDEOGRAPH>$" "IDEOGRAPH, LAST>")
      (str/replace "SYLLABLE>.." "SYLLABLE, FIRST>..")
      (str/replace #"SYLLABLE>$" "SYLLABLE, LAST>")
      (str/replace "SURROGATE>.." "SURROGATE, FIRST>..")
      (str/replace #"SURROGATE>$" "SURROGATE, LAST>")
      (str/replace "NONCHARACTER" "NOT A CHARACTER")))

(defn codepoint-range-name
  "Handles naming for assigned codepoints in the unicode data that use the First/Last range shortcut

   Example:
   AC00;<Hangul Syllable, First>;Lo;0;L;;;;;N;;;;;
   D7A3;<Hangul Syllable, Last>;Lo;0;L;;;;;N;;;;;

   every codepoint in between those has the name <Hangul Syllable>"

  [cp]
  (cond
    (<= 0x3400 cp 0x4DBF)     "<CJK Ideograph Extension A>"
    (<= 0x4E00 cp 0x9FFF)     "<CJK Ideograph>"
    (<= 0xF900 cp 0xFAFF)     "<CJK Compatibility Ideograph>"
    (<= 0x20000 cp 0x2A6DF)   "<CJK Ideograph Extension B>"
    (<= 0x2A700 cp 0x2B73F)   "<CJK Ideograph Extension C>"
    (<= 0x2B740 cp 0x2B81F)   "<CJK Ideograph Extension D>"
    (<= 0x2B820 cp 0x2CEAF)   "<CJK Ideograph Extension E>"
    (<= 0x2CEB0 cp 0x2EBEF)   "<CJK Ideograph Extension F>"
    (<= 0x30000 cp 0x3134A)   "<CJK Ideograph Extension G>"
    (<= 0x17000 cp 0x187F7)   "<Tangut Ideograph>"
    (<= 0x18D00 cp 0x18D08)   "<Tangut Ideograph Supplement>"
    (<= 0xAC00 cp 0xD7AF)     "<Hangul Syllable>"
    (<= 0xD800 cp 0xDBFF)     "<Non Private Use High Surrogate>"
    (<= 0xDC00 cp 0xDFFF)     "<Low Surrogate>"
    (<= 0xE000 cp 0xF8FF)     "<Private Use>"
    (<= 0xF0000 cp 0xFFFFD)   "<Plane 15 Private Use>"
    (<= 0x100000 cp 0x10FFFD) "<Plane 16 Private Use>"
    :else                     nil))

(defn get-character-name
  "Get Unicode character name for a codepoint, using Unicode 1.0 name for control characters"
  [unicode-data cp]
  (let [name           (get-in unicode-data [cp :name])
        unicode10-name (get-in unicode-data [cp :unicode10-name])
        assigned?      (contains? unicode-data cp)]
    (if (and (= name "<control>") (not (str/blank? unicode10-name)))
      unicode10-name
      (or name
          (when assigned? (codepoint-range-name cp))
          (cond
            (non-character? cp)
            "<NONCHARACTER>"
            :else (format "<UNASSIGNED-%04X>" cp))))))

(defn iana-range-description [unicode-data start end]
  (-> (if (= start end)
        (get-character-name unicode-data start)
        (format "%s..%s"
                (get-character-name unicode-data start)
                (get-character-name unicode-data end)))
      iana-compatible-name))

(defn ucd-style-name
  "Get UCD-style character name for xmlrfc format"
  [unicode-data cp]
  (let [name      (get-in unicode-data [cp :name])
        assigned? (contains? unicode-data cp)]
    (cond
      (codepoint-range-name cp)
      (codepoint-range-name cp)

      (nil? name)
      (format "<UNASSIGNED-%04X>" cp)

      (= name "<control>") "<control>"

      (and assigned? (non-character? cp))
      "<noncharacter>"

      (not assigned?)
      "<reserved>"

      :else name)))

(defn ucd-range-description
  "Get UCD-style range description for xmlrfc format"
  [unicode-data start end]
  (if (= start end)
    (ucd-style-name unicode-data start)
    (format "%s..%s"
            (ucd-style-name unicode-data start)
            (ucd-style-name unicode-data end))))

(defn iana-prop-name [prop]
  (case prop
    :free-pval  "ID_DIS or FREE_PVAL"
    :pvalid     "PVALID"
    :disallowed "DISALLOWED"
    :unassigned "UNASSIGNED"
    :contextj   "CONTEXTJ"
    :contexto   "CONTEXTO"
    (str/upper-case (name prop))))

(defn write-iana-csv
  "Write IANA CSV format with proper escaping and range compression"
  [output-file unicode-data properties]
  (let [ranges (compress-ranges-vec properties :with-reason? false)]
    (println (format "  Writing iana.csv (%,d ranges)" (count ranges)))

    (with-open [writer (io/writer output-file)]
      (.write writer "Codepoint,Property,Description\r\n")

      (doseq [[start end [prop _reason]] ranges]
        (let [range-str   (if (= start end)
                            (format "%04X" start)
                            (format "%04X-%04X" start end))
              prop-str    (iana-prop-name prop)
              description (iana-range-description unicode-data start end)]

          (.write writer (format "%s,%s,%s\r\n"
                                 range-str
                                 prop-str
                                 ;; Quote description if it contains commas
                                 (if (str/includes? description ",")
                                   (format "\"%s\"" description)
                                   description))))))))
(defn write-python-txt
  [output-dir props version]
  (let [output-file (str output-dir "/" (python-filename-format version))]
    (println "  Writing " (python-filename-format version))
    (.mkdirs (io/file generated-dir))
    (let [ranges (compress-ranges-vec props :with-reason? true)]
      (with-open [writer (io/writer output-file)]
        (doseq [[start end prop-tuple] ranges]
          (let [[prop reason] prop-tuple
                range-str     (format "%04X-%04X" start end)
                prop-str      (-> prop name str/upper-case (str/replace #"-" "_"))
                reason-str    (-> reason name (str/replace #"-" "_"))
                line          (format "%s %s/%s" range-str prop-str reason-str)]
            (.write writer (str line "\n"))))))))

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
