#!/usr/bin/env bb
(ns createtables
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [common :as common :refer [all-codepoints]]))

(def unicode-base-path "data")
(def output-base-path "tables")

(defn load-unicode-data
  "Load Unicode data files for a specific version"
  [version]
  (let [version-dir (str unicode-base-path "/" version)]
    (when-not (.exists (io/file version-dir))
      (throw (ex-info "Unicode data directory not found"
                      {:version version :path version-dir})))

    (let [unicode-data-file  (str version-dir "/UnicodeData.txt")
          derived-props-file (str version-dir "/DerivedCoreProperties.txt")]

      (when-not (.exists (io/file unicode-data-file))
        (throw (ex-info "UnicodeData.txt not found"
                        {:version version :path unicode-data-file})))

      (when-not (.exists (io/file derived-props-file))
        (throw (ex-info "DerivedCoreProperties.txt not found"
                        {:version version :path derived-props-file})))

      {:unicode-data  (common/parse-unicode-data unicode-data-file)
       :derived-props (common/parse-derived-core-properties derived-props-file)})))

(defn compute-all-precis-properties
  "Compute PRECIS properties for all codepoints efficiently using unified function"
  [version unicode-data derived-props]
  (println (format "Computing PRECIS properties for %,d codepoints..." (count all-codepoints)))

  ;; Use transient for efficiency during bulk construction
  (loop [result    (transient {})
         remaining all-codepoints
         count     0]
    (if (empty? remaining)
      (persistent! result)
      (let [cp   (first remaining)
            ;; Use unified derive-precis-property function with version awareness
            prop (common/derive-precis-property unicode-data derived-props cp)]
        (recur (assoc! result cp prop)
               (rest remaining)
               (inc count))))))

(defn determine-rules
  "Determine PRECIS rules that apply to a codepoint based on RFC 8264 Section 9"
  [unicode-data derived-props cp]
  (let [assigned? (contains? unicode-data cp)]
    (cond
      (common/unassigned? unicode-data cp)          "J"
      (common/ascii7? cp)                           "K"
      (common/join-control? cp)                     "H"
      (common/old-hangul-jamo? cp)                  "I"
      (common/precis-ignorable? derived-props cp)   "M"
      (common/control? unicode-data cp)             "L"
      (common/has-compat? cp)                       "F"
      (common/letter-digits? unicode-data cp)       "A"
      (common/other-letter-digits? unicode-data cp) "B"
      (common/spaces? unicode-data cp)              "N"
      (common/symbols? unicode-data cp)             "O"
      (common/punctuation? unicode-data cp)         "P"
      :else                                         "")))

(defn write-allcodepoints-txt
  "Write allcodepoints.txt format: <codepoint>;<property>;<rules>;<name>"
  [output-dir _version unicode-data derived-props properties]
  (let [output-file (str output-dir "/allcodepoints.txt")]
    (println (format "  Writing allcodepoints.txt (%,d entries)" (count all-codepoints)))

    (with-open [writer (io/writer output-file)]
      (doseq [cp all-codepoints]
        (let [prop  (get properties cp :unassigned)
              rules (determine-rules unicode-data derived-props cp)
              name  (common/get-character-name unicode-data cp)]
          (.write writer (format "%04X;%s;%s;%s\n"
                                 cp (common/precis-properties prop) rules name)))))))

(defn load-scripts-data
  "Load Unicode Scripts.txt data"
  [version-dir]
  (let [scripts-file (str version-dir "/Scripts.txt")]
    (when (.exists (io/file scripts-file))
      (with-open [reader (io/reader scripts-file)]
        (->> (line-seq reader)
             (remove #(or (str/blank? %) (str/starts-with? % "#")))
             (map (fn [line]
                    (let [[range-part script-part] (str/split line #"\s*;\s*" 2)
                          script                   (str/trim (first (str/split script-part #"\s*#")))]
                      (if (str/includes? range-part "..")
                        (let [[start-str end-str] (str/split range-part #"\.\.")]
                          {:start  (Integer/parseInt start-str 16)
                           :end    (Integer/parseInt end-str 16)
                           :script script})
                        {:start  (Integer/parseInt range-part 16)
                         :end    (Integer/parseInt range-part 16)
                         :script script}))))
             (reduce (fn [acc {:keys [start end script]}]
                       (reduce #(assoc %1 %2 script) acc (range start (inc end))))
                     {}))))))

(defn get-script
  "Get script for a codepoint"
  [scripts-data cp]
  (get scripts-data cp "Common"))

(defn html-escape-name
  "Escape character name for HTML display, using <control> for control characters"
  [unicode-data cp]
  (let [raw-name     (common/get-character-name unicode-data cp)
        display-name (if (= (get-in unicode-data [cp :name]) "<control>")
                       "<control>"
                       raw-name)]
    (-> display-name
        (str/replace "&" "&amp;")
        (str/replace "<" "&lt;")
        (str/replace ">" "&gt;"))))

(defn write-html-row
  "Write a single HTML table row for a codepoint"
  [writer unicode-data derived-props properties cp]
  (let [prop         (get properties cp :unassigned)
        rules        (determine-rules unicode-data derived-props cp)
        gc           (get-in unicode-data [cp :general-category] "Cn")
        name         (html-escape-name unicode-data cp)
        char-display (format "&#x%04X;" cp)]
    (.write writer (format "<TR><TD>U+%04X</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD></TR>\n\n"
                           cp char-display (common/precis-properties prop) rules gc name))))

(defn write-html-group
  "Write HTML table for a group of codepoints"
  [writer group-name codepoints unicode-data derived-props properties]
  (.write writer (format "<H3>%s</H3>\n" group-name))
  (.write writer "<TABLE border=1><TR><TH>Code(s)</TH><TH>Char</TD><TH>U-label</TH><TH>Rules</TH><TH>GC</TD><TH>Name(s)</TH></TR>\n")
  (doseq [cp (sort codepoints)]
    (write-html-row writer unicode-data derived-props properties cp))
  (.write writer "</TABLE>\n\n"))

(defn write-byscript-html
  "Write HTML table sorted by Unicode script"
  [output-dir version unicode-data derived-props properties]
  (let [output-file   (str output-dir "/byscript.html")
        version-dir   (str "data/" version)
        scripts-data  (load-scripts-data version-dir)
        script-groups (->> (keys unicode-data)
                           (group-by #(get-script scripts-data %))
                           (into (sorted-map)))]

    (println (format "  Writing byscript.html (grouped by %d scripts)" (count script-groups)))

    (with-open [writer (io/writer output-file)]
      (.write writer "<HTML>\n<BODY>\n<H3>Codepoints by script</H3>\n")
      (doseq [[script codepoints] script-groups]
        (write-html-group writer script codepoints unicode-data derived-props properties))
      (.write writer "</BODY></HTML>\n"))))

(defn write-bygc-html
  "Write HTML table sorted by General Category"
  [output-dir _version unicode-data derived-props properties]
  (let [output-file (str output-dir "/bygc.html")
        gc-groups   (->> (keys unicode-data)
                         (group-by #(get-in unicode-data [% :general-category] "Cn"))
                         (into (sorted-map)))]

    (println (format "  Writing bygc.html (grouped by %d general categories)" (count gc-groups)))

    (with-open [writer (io/writer output-file)]
      (.write writer "<HTML>\n<BODY>\n<H3>Codepoints by GeneralCategory</H3>\n")
      (doseq [[gc codepoints] gc-groups]
        (write-html-group writer gc codepoints unicode-data derived-props properties))
      (.write writer "</BODY></HTML>\n"))))

(defn xml-escape
  "Escape text for XML with optional quote escaping"
  ([text] (xml-escape text false))
  ([text escape-quotes?]
   (cond-> text
     true           (str/replace "&" "&amp;")
     true           (str/replace "<" "&lt;")
     true           (str/replace ">" "&gt;")
     escape-quotes? (str/replace "\"" "&quot;"))))

(defn format-range
  "Format codepoint range with specified separator"
  [start end separator]
  (if (= start end)
    (format "%04X" start)
    (format "%04X%s%04X" start separator end)))

(defn write-with-progress
  "Execute function with file writer and progress message"
  [output-file format-msg count-val write-fn]
  (println (format format-msg count-val))
  (with-open [writer (io/writer output-file)]
    (write-fn writer)))

(defn write-xmlrfc-xml
  "Write UCD-style range notation format with XML wrapper elements"
  [output-dir _version unicode-data properties]
  (let [output-file (str output-dir "/xmlrfc.xml")
        ranges      (common/compress-ranges properties)]
    (write-with-progress output-file "  Writing xmlrfc.xml (%,d ranges)" (count ranges)
                         (fn [writer]
                           (.write writer "<section title=\"Codepoints in Unicode Character Database (UCD) format\">\n")
                           (.write writer "<figure><artwork>\n")

                           (doseq [[start end prop] ranges]
                             (let [range-str (format-range start end "..")
                                   comment   (xml-escape (common/ucd-range-description unicode-data start end))
                                   line      (format "%-12s; %-11s # %s" range-str (common/precis-properties prop) comment)]
                               (.write writer (str line "\n"))))

                           (.write writer "</artwork></figure></section>\n")))))

(defn write-iana-xml
  "Write IANA XML registry format"
  [output-dir _version unicode-data properties]
  (let [output-file (str output-dir "/idnabis-tables.xml")
        ranges      (common/compress-ranges properties)]
    (write-with-progress output-file "  Writing idnabis-tables.xml (%,d records)" (count ranges)
                         (fn [writer]
                           (.write writer "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                           (.write writer "<registry xmlns=\"http://www.iana.org/assignments\" id=\"precis-tables\">\n")

                           (doseq [[start end prop] ranges]
                             (let [codepoint-str (format-range start end "-")
                                   description   (xml-escape (common/iana-range-description unicode-data start end) true)]
                               (.write writer "  <record>")
                               (.write writer (format "<codepoint>%s</codepoint>" codepoint-str))
                               (.write writer (format "<property>%s</property>" (common/precis-properties prop)))
                               (.write writer (format "<description>%s</description>" description))
                               (.write writer "</record>\n")))

                           (.write writer "</registry>\n")))))

(defn generate-for-version
  "Generate all output formats for a specific Unicode version"
  [version]
  (println (format "Generating PRECIS tables for Unicode %s..." version))

  (let [output-dir                           (str output-base-path "/" version)
        {:keys [unicode-data derived-props]} (load-unicode-data version)
        properties                           (compute-all-precis-properties version unicode-data derived-props)]

    (.mkdirs (io/file output-dir))

    (write-allcodepoints-txt output-dir version unicode-data derived-props properties)
    (write-byscript-html output-dir version unicode-data derived-props properties)
    (write-bygc-html output-dir version unicode-data derived-props properties)
    (write-xmlrfc-xml output-dir version unicode-data properties)
    (write-iana-xml output-dir version unicode-data properties)
    (common/write-iana-csv (str output-dir "/iana.csv") unicode-data properties)

    (println (format "Generated 6 files for Unicode %s in %s" version output-dir))))

(defn get-available-versions
  "Get list of available Unicode versions from data directory"
  []
  (let [data-dir (io/file unicode-base-path)]
    (if (.exists data-dir)
      (->> (.listFiles data-dir)
           (filter #(.isDirectory %))
           (map #(.getName %))
           (filter #(re-matches #"\d+\.\d+\.\d+" %))
           sort
           vec)
      [])))

(defn -main
  "Main entry point for createtables script"
  [& args]
  (try
    (let [requested-versions (if (empty? args)
                               (get-available-versions)
                               (if (= (first args) "all")
                                 (get-available-versions)
                                 (vec args)))
          available          (set (get-available-versions))
          valid-versions     (filter available requested-versions)
          invalid-versions   (remove available requested-versions)]

      ;; Report invalid versions
      (when (seq invalid-versions)
        (doseq [version invalid-versions]
          (println (format "Warning: Unicode data for version %s not found, skipping" version))))

      ;; Generate for valid versions
      (if (empty? valid-versions)
        (println "No valid Unicode versions found. Run 'bb download' first.")
        (do
          (println (format "Generating PRECIS tables for %d Unicode versions: %s"
                           (count valid-versions) (str/join ", " valid-versions)))
          (doseq [version valid-versions]
            (generate-for-version version))
          (println (format "Complete! Generated tables for %d versions in %s/"
                           (count valid-versions) output-base-path)))))

    (catch Exception e
      (println (format "Error: %s" (.getMessage e)))
      (when-let [data (ex-data e)]
        (println (format "Details: %s" data)))
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
