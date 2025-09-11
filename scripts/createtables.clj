#!/usr/bin/env bb
(ns createtables
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [common :as common]))

(def unicode-base-path "data")
(def output-base-path "tables")

;; All Unicode codepoints 0x0000 to 0x10FFFF
(def all-codepoints (range 0x0000 0x110000))

;; Available Unicode versions
(def available-versions 
  ["6.3.0" "7.0.0" "8.0.0" "9.0.0" "10.0.0" "11.0.0" "12.0.0" 
   "13.0.0" "14.0.0" "15.0.0" "15.1.0" "16.0.0" "17.0.0"])

(defn load-unicode-data
  "Load Unicode data files for a specific version"
  [version]
  (let [version-dir (str unicode-base-path "/" version)]
    (when-not (.exists (io/file version-dir))
      (throw (ex-info "Unicode data directory not found" 
                      {:version version :path version-dir})))
    
    (let [unicode-data-file (str version-dir "/UnicodeData.txt")
          derived-props-file (str version-dir "/DerivedCoreProperties.txt")]
      
      (when-not (.exists (io/file unicode-data-file))
        (throw (ex-info "UnicodeData.txt not found" 
                        {:version version :path unicode-data-file})))
      
      (when-not (.exists (io/file derived-props-file))
        (throw (ex-info "DerivedCoreProperties.txt not found" 
                        {:version version :path derived-props-file})))
      
      {:unicode-data (common/parse-unicode-data unicode-data-file)
       :derived-props (common/parse-derived-core-properties derived-props-file)})))

(defn compute-all-precis-properties
  "Compute PRECIS properties for all codepoints efficiently using unified function"
  [version unicode-data derived-props]
  (println (format "Computing PRECIS properties for %,d codepoints..." (count all-codepoints)))
  
  ;; Use transient for efficiency during bulk construction
  (loop [result (transient {})
         remaining all-codepoints
         count 0]
    (if (empty? remaining)
      (persistent! result)
      (let [cp (first remaining)
            ;; Use unified derive-precis-property function with version awareness
            prop (common/derive-precis-property unicode-data derived-props cp version)]
        (recur (assoc! result cp prop)
               (rest remaining)
               (inc count))))))

(defn determine-rules
  "Determine PRECIS rules that apply to a codepoint (simplified version)"
  [unicode-data derived-props cp prop]
  ;; This is a simplified version - a full implementation would need
  ;; to track which specific RFC 8264 Section 9 rules were applied
  (case prop
    :pvalid "AE"  ; Simplified - could be A, E, or both
    :free-pval "O" ; Simplified
    :disallowed ""
    :unassigned ""
    :contextj "H"
    :contexto "B" 
    ""))

(defn write-allcodepoints-txt
  "Write allcodepoints.txt format: <codepoint>;<property>;<rules>;<name>"
  [output-dir version unicode-data derived-props properties]
  (let [output-file (str output-dir "/allcodepoints.txt")]
    (println (format "  Writing allcodepoints.txt (%,d entries)" (count all-codepoints)))
    
    (with-open [writer (io/writer output-file)]
      (doseq [cp all-codepoints]
        (let [prop (get properties cp :unassigned)
              prop-str (get common/precis-properties prop (str/upper-case (name prop)))
              rules (determine-rules unicode-data derived-props cp prop)
              name (common/get-character-name unicode-data cp)]
          (.write writer (format "%04X;%s;%s;%s\n" 
                                cp prop-str rules name)))))))

(defn write-byscript-html
  "Write HTML table sorted by Unicode script (placeholder implementation)"
  [output-dir version unicode-data derived-props properties]
  (let [output-file (str output-dir "/byscript.html")]
    (println "  Writing byscript.html (grouped by scripts)")
    
    (with-open [writer (io/writer output-file)]
      (.write writer "<HTML><HEAD><TITLE>PRECIS by Script</TITLE></HEAD><BODY>\n")
      (.write writer "<TABLE border=1>\n")
      (.write writer "<TR><TH>Code(s)</TH><TH>Char</TH><TH>U-label</TH><TH>Rules</TH><TH>GC</TH><TH>Name(s)</TH></TR>\n")
      
      ;; Simple implementation - just output first 100 codepoints as example
      (doseq [cp (take 100 all-codepoints)]
        (let [prop (get properties cp :unassigned)
              prop-str (get common/precis-properties prop (str/upper-case (name prop)))
              rules (determine-rules unicode-data derived-props cp prop)
              gc (get-in unicode-data [cp :general-category] "Cn")
              name (common/get-character-name unicode-data cp)
              char-display (if (<= 0x20 cp 0x7E) 
                            (format "&#x%04X;" cp)
                            "&nbsp;")]
          (.write writer (format "<TR><TD>U+%04X</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD></TR>\n"
                                cp char-display prop-str rules gc name))))
      
      (.write writer "</TABLE>\n</BODY></HTML>\n"))))

(defn write-bygc-html
  "Write HTML table sorted by General Category (placeholder implementation)"
  [output-dir version unicode-data derived-props properties]
  (let [output-file (str output-dir "/bygc.html")]
    (println "  Writing bygc.html (grouped by general categories)")
    
    (with-open [writer (io/writer output-file)]
      (.write writer "<HTML><HEAD><TITLE>PRECIS by General Category</TITLE></HEAD><BODY>\n")
      (.write writer "<TABLE border=1>\n")
      (.write writer "<TR><TH>Code(s)</TH><TH>Char</TH><TH>U-label</TH><TH>Rules</TH><TH>GC</TH><TH>Name(s)</TH></TR>\n")
      
      ;; Simple implementation - just output first 100 codepoints as example
      (doseq [cp (take 100 all-codepoints)]
        (let [prop (get properties cp :unassigned)
              prop-str (get common/precis-properties prop (str/upper-case (name prop)))
              rules (determine-rules unicode-data derived-props cp prop)
              gc (get-in unicode-data [cp :general-category] "Cn")
              name (common/get-character-name unicode-data cp)
              char-display (if (<= 0x20 cp 0x7E) 
                            (format "&#x%04X;" cp)
                            "&nbsp;")]
          (.write writer (format "<TR><TD>U+%04X</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD><TD>%s</TD></TR>\n"
                                cp char-display prop-str rules gc name))))
      
      (.write writer "</TABLE>\n</BODY></HTML>\n"))))

(defn write-xmlrfc-xml
  "Write UCD-style range notation format"
  [output-dir version unicode-data properties]
  (let [output-file (str output-dir "/xmlrfc.xml")
        ranges (common/compress-ranges properties)]
    (println (format "  Writing xmlrfc.xml (%,d ranges)" (count ranges)))
    
    (with-open [writer (io/writer output-file)]
      (doseq [[start end prop] ranges]
        (let [prop-str (get common/precis-properties prop (str/upper-case (name prop)))
              range-str (if (= start end)
                          (format "%04X" start)
                          (format "%04X..%04X" start end))
              start-name (common/get-character-name unicode-data start)
              end-name (if (= start end) 
                        start-name 
                        (common/get-character-name unicode-data end))
              comment (if (= start end)
                       start-name
                       (format "%s..%s" start-name end-name))
              ;; Format with proper alignment
              line (format "%-12s ; %-11s # %s" range-str prop-str comment)]
          (.write writer (str line "\n")))))))

(defn write-iana-xml
  "Write IANA XML registry format"
  [output-dir version unicode-data properties]
  (let [output-file (str output-dir "/idnabis-tables.xml")
        ranges (common/compress-ranges properties)]
    (println (format "  Writing idnabis-tables.xml (%,d records)" (count ranges)))
    
    (with-open [writer (io/writer output-file)]
      (.write writer "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
      (.write writer "<registry xmlns=\"http://www.iana.org/assignments\" id=\"precis-tables\">\n")
      
      (doseq [[start end prop] ranges]
        (let [prop-str (get common/precis-properties prop (str/upper-case (name prop)))
              codepoint-str (if (= start end)
                             (format "%04X" start)
                             (format "%04X-%04X" start end))
              start-name (common/get-character-name unicode-data start)
              end-name (if (= start end) 
                        start-name 
                        (common/get-character-name unicode-data end))
              description (if (= start end)
                           start-name
                           (format "%s..%s" start-name end-name))]
          (.write writer "  <record>\n")
          (.write writer (format "    <codepoint>%s</codepoint>\n" codepoint-str))
          (.write writer (format "    <property>%s</property>\n" prop-str))
          (.write writer (format "    <description>%s</description>\n" description))
          (.write writer "  </record>\n")))
      
      (.write writer "</registry>\n"))))

(defn write-iana-csv
  "Write IANA CSV format with proper escaping"
  [output-dir version unicode-data properties]
  (let [output-file (str output-dir "/iana.csv")
        ranges (common/compress-ranges properties)]
    (println (format "  Writing iana.csv (%,d records)" (count ranges)))
    
    (with-open [writer (io/writer output-file)]
      (doseq [[start end prop] ranges]
        (let [prop-str (get common/precis-properties prop (str/upper-case (name prop)))
              codepoint-str (if (= start end)
                             (format "%04X" start)
                             (format "%04X-%04X" start end))
              start-name (common/get-character-name unicode-data start)
              end-name (if (= start end) 
                        start-name 
                        (common/get-character-name unicode-data end))
              description (if (= start end)
                           start-name
                           (format "%s..%s" start-name end-name))
              ;; Escape description if it contains commas
              escaped-desc (if (str/includes? description ",")
                            (format "\"%s\"" description)
                            description)]
          (.write writer (format "%s,%s,%s\r\n" codepoint-str prop-str escaped-desc)))))))

(defn generate-for-version
  "Generate all output formats for a specific Unicode version"
  [version]
  (println (format "Generating PRECIS tables for Unicode %s..." version))
  
  (let [output-dir (str output-base-path "/" version)
        {:keys [unicode-data derived-props]} (load-unicode-data version)
        properties (compute-all-precis-properties version unicode-data derived-props)]
    
    ;; Create output directory
    (.mkdirs (io/file output-dir))
    
    ;; Generate all output formats
    (write-allcodepoints-txt output-dir version unicode-data derived-props properties)
    (write-byscript-html output-dir version unicode-data derived-props properties)
    (write-bygc-html output-dir version unicode-data derived-props properties)
    (write-xmlrfc-xml output-dir version unicode-data properties)
    (write-iana-xml output-dir version unicode-data properties)
    (write-iana-csv output-dir version unicode-data properties)
    
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
          available (set (get-available-versions))
          valid-versions (filter available requested-versions)
          invalid-versions (remove available requested-versions)]
      
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