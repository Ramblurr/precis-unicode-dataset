#!/usr/bin/env bb
(ns changes
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [common :as common]))

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
  "Write an ietf rfc table file

  changes should be a map of codepoints -> [derived-property-value rule-reason]"
  [output-file unicode-data changes]
  (when (seq changes)
    (let [ranges (common/compress-ranges-map changes)]
      (println "  Writing " output-file)
      (with-open [writer (io/writer output-file)]
        (let [lines     (for [[start end [prop _reason]] ranges]
                          (let [range-str  (common/range-format [start end])
                                prop-str   (get common/precis-properties prop "UNKNOWN")
                                comment    (common/ucd-range-description unicode-data start end)
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

(defn change-table
  "Produces a tuple of [assigned existing-change]

  where from-unassigned is a map of codepoint -> [property-value rule-reason]"
  [from-props to-props]
  (let [[assigned  existing-change]
        (reduce (fn [[assigned existing-change] cp]
                  (let [[from-prop _from-reason] (nth from-props cp)
                        [to-prop to-reason]      (nth to-props cp)]
                    [(if (and (= from-prop :unassigned) (not= to-prop :unassigned))
                       (assoc! assigned cp [to-prop to-reason])
                       assigned)
                     (if (and (not= from-prop :unassigned) (not= from-prop to-prop))
                       (assoc! existing-change cp [to-prop to-reason])
                       existing-change)]))
                [(transient {}) (transient {})]
                common/all-codepoints)]

    [(persistent! assigned) (persistent! existing-change)]))

(defn generate-change-table
  "Generate change tables using pre-computed properties"
  [all-props from-version to-version]
  (let [from-props                 (get-in all-props [from-version :props])
        to-props                   (get-in all-props [to-version :props])
        to-unicode                 (get-in all-props [to-version :unicode-data])
        [assigned existing-change] (change-table from-props to-props)
        output-file                (str output-base-path "/changes-" from-version "-" to-version)]
    (.mkdirs (io/file output-base-path))
    (when (some some? assigned)
      (write-table-file (str output-file "-from-unassigned.txt") to-unicode assigned))
    (when (some some? existing-change)
      (let [suffix "-property-changes"]
        (write-table-file (str output-file suffix ".txt") to-unicode existing-change)))))

(defn build-version-properties [version]
  (let [unicode-dir   (str unicode-base-path "/" version)
        unicode-data  (common/parse-unicode-data (str unicode-dir "/UnicodeData.txt"))
        derived-props (common/parse-derived-core-properties (str unicode-dir "/DerivedCoreProperties.txt"))
        props-vec     (common/build-props-vector unicode-data derived-props)]
    [version {:props props-vec :unicode-data unicode-data :version version}]))

(defn build-all-version-properties
  "Build PRECIS properties for all discovered Unicode versions (single pass per version)"
  [versions]
  (println (format "Deriving PRECIS property values for unicode %s in parallel" (str/join ", " versions)))
  (->> versions
       (pmap build-version-properties)
       (into {})))

(defn write-python-txt
  [props version]
  (let [output-file (str output-base-path "/" (common/python-filename-format version))]
    (println "  Writing " output-file)
    (.mkdirs (io/file output-base-path))
    (let [ranges (common/compress-ranges-vec props :with-reason? true)]
      (with-open [writer (io/writer output-file)]
        (doseq [[start end prop-tuple] ranges]
          (let [[prop reason] prop-tuple
                range-str     (format "%04X-%04X" start end)
                prop-str      (-> prop name str/upper-case (str/replace #"-" "_"))
                reason-str    (-> reason name (str/replace #"-" "_"))
                line          (format "%s %s/%s" range-str prop-str reason-str)]
            (.write writer (str line "\n"))))))))

(defn write-python-txt-all-versions
  "Generate Python txt files for all Unicode versions"
  [all-props]
  (println "Generating Python txt files for " (keys all-props))
  (doseq [{:keys [version props]} (vals all-props)]
    (write-python-txt props version)))

(defn write-iana-6.3-edn
  "Save complete PRECIS mappings to file for verification"
  [mappings]
  (let [output-file (str output-base-path "/precis-mappings-6.3.0.edn")]
    (.mkdirs (io/file output-base-path))
    (spit output-file (pr-str mappings))))

(defn write-iana-6.3-csv
  "Generate IANA-compatible CSV output format matching exact IANA formatting"
  [mappings unicode-data]
  (let [output-file (str output-base-path "/precis-tables-6.3.0.csv")]
    (println (format "Generating IANA-compatible CSV: %s" output-file))
    (.mkdirs (io/file output-base-path))
    (common/write-iana-csv output-file unicode-data mappings)))

(defn generate-iana-outputs
  "Generate IANA-specific outputs for Unicode 6.3.0"
  [all-props]
  (when-let [{:keys [props unicode-data]} (get all-props "6.3.0")]
    (println "Generating IANA outputs for Unicode 6.3.0...")
    (write-iana-6.3-edn props)
    (write-iana-6.3-csv props unicode-data)))

(defn generate-change-tables
  "Generate change tables between adjacent Unicode versions"
  [all-props versions]
  (when (seq versions)
    (println "Generating PRECIS Unicode change tables...")
    (dorun
     (map (fn [[from to]]
            (generate-change-table all-props from to))
          (partition 2 1 versions)))
    (println "Done! Check" output-base-path "for generated tables.")))

(defn match-versions
  [unicode-versions args]
  (if args
    (sort common/version-compare (into [] (set/intersection (set unicode-versions) (set args))))
    unicode-versions))

(defn -main [& args]
  (let [unicode-versions (common/discover-unicode-versions unicode-base-path)
        version-subset   (match-versions unicode-versions args)]
    (if (seq version-subset)
      (let [all-props (build-all-version-properties version-subset)]
        (generate-iana-outputs all-props)
        (write-python-txt-all-versions all-props)
        (if (<= (count version-subset) 1)
          (println "Cannot generate change tables when only one version is provided")
          (generate-change-tables all-props version-subset))
        (println "All outputs generated! Run: bb verify"))
      (println "No Unicode versions found in" unicode-base-path))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
