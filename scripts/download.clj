#!/usr/bin/env bb
(ns download
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [common :as common]
   [rewrite-clj.node :as n]
   [rewrite-clj.zip :as z]))

(def data-dir "data")
(def unicode-versions ["6.3.0" "7.0.0" "8.0.0" "9.0.0" "10.0.0" "11.0.0" "12.0.0" "13.0.0" "14.0.0" "15.0.0" "16.0.0" "17.0.0"])

(def unicode-files
  "Core Unicode data files needed for PRECIS property derivation"
  ["UnicodeData.txt"
   "DerivedCoreProperties.txt"
   "Scripts.txt"
   "Blocks.txt"
   "CompositionExclusions.txt"
   "HangulSyllableType.txt"
   "CaseFolding.txt"
   "DerivedNormalizationProps.txt"])

(defn download-file
  "Download a file from URL to local path"
  [url local-path filename]
  (println (format "  Downloading %s..." filename))
  (.mkdirs (.getParentFile (io/file local-path)))
  (with-open [in  (io/input-stream url)
              out (io/output-stream local-path)]
    (io/copy in out)))

(defn process-file-download
  "Process download for a single file, returning result status"
  [base-url version-dir filename]
  (let [url        (str base-url filename)
        local-path (str version-dir "/" filename)
        file       (io/file local-path)]
    (if (.exists file)
      {:filename filename :status :skipped}
      (try
        (download-file url local-path filename)
        {:filename filename :status :downloaded}
        (catch Exception e
          (println (format "  Warning: Could not download %s: %s" filename (.getMessage e)))
          {:filename filename :status :failed})))))

(defn download-unicode-files
  "Download Unicode data files for a specific version"
  [version]
  (let [base-url    (str "https://www.unicode.org/Public/" version "/ucd/")
        version-dir (str data-dir "/" version)]
    (.mkdirs (io/file version-dir))

    (let [results          (mapv #(process-file-download base-url version-dir %) unicode-files)
          downloaded-count (count (filter #(= (:status %) :downloaded) results))
          skipped-count    (count (filter #(= (:status %) :skipped) results))]

      (when (pos? downloaded-count)
        (println (format "\nUnicode %s:" version)))

      {:version    version
       :downloaded downloaded-count
       :skipped    skipped-count
       :results    results})))

(defn download-precis-python-refs
  "Download reference derived-props files from precis_i18n project"
  []
  (let [base-url "https://raw.githubusercontent.com/byllyfish/precis_i18n/main/test/"
        ref-dir  "reference/tables-extracted"
        files    (map common/python-filename-format unicode-versions)]
    (.mkdirs (io/file ref-dir))
    (println "Downloading PRECIS Python reference files...")
    (doseq [filename files]
      (let [url        (str base-url filename)
            local-path (str ref-dir "/" filename)
            file       (io/file local-path)]
        (when-not (.exists file)
          (try
            (download-file url local-path filename)
            (catch Exception e
              (println (format "Warning: Could not download %s: %s" filename (.getMessage e))))))))))

(defn ensure-reference-files
  "Download required reference files if they don't exist"
  []
  (let [files [{:url   "https://www.iana.org/assignments/precis-tables-6.3.0/precis-tables-6.3.0.csv"
                :local (str common/extracted-dir "/precis-tables-6.3.0.csv")}
               {:url   "https://raw.githubusercontent.com/stpeter/draft-nemoto-precis-unicode/main/draft-nemoto-precis-unicode.md"
                :local (str common/extracted-dir "/draft-nemoto-precis-unicode.md")}]]
    (doseq [{:keys [url local]} files]
      (let [file     (io/file local)
            filename (.getName file)]
        (when-not (.exists file)
          (try
            (download-file url local filename)
            (catch Exception e
              (println (format "Warning: Could not download %s: %s" url (.getMessage e))))))))))

(defn sort-unicode-versions
  "Sort Unicode versions using semantic version comparison"
  [versions]
  (->> versions
       (filter (fn [v] (and v (not (str/blank? v)))))
       (sort common/version-compare)))

(defn discover-new-versions
  "Discover new Unicode versions by checking what's available online vs hardcoded list"
  []
  (let [latest       (common/latest-unicode-version)
        existing-set (set unicode-versions)]
    (if latest
      (let [all-versions    (conj unicode-versions latest)
            sorted-versions (sort-unicode-versions (set all-versions))
            new-versions    (remove existing-set sorted-versions)]
        {:latest   latest
         :existing unicode-versions
         :new      new-versions
         :all      sorted-versions})
      {:latest   nil
       :existing unicode-versions
       :new      []
       :all      unicode-versions})))

(defn update-unicode-versions-in-file
  "Update the unicode-versions definition in this file"
  [new-versions]
  (let [script-file "scripts/download.clj"
        writable?   (fs/writable? script-file)]
    (if writable?
      (try
        (let [content         (slurp script-file)
              sorted-versions (sort-unicode-versions new-versions)
              string-nodes    (mapcat (fn [version]
                                        [(n/string-node version)
                                         (n/whitespace-node " ")])
                                      sorted-versions)
              trimmed-nodes   (butlast string-nodes)
              zloc            (z/of-string content)
              updated-zloc    (-> zloc
                                  (z/find-value z/next 'unicode-versions)
                                  z/right
                                  (z/replace (n/vector-node trimmed-nodes)))]
          (spit script-file (z/root-string updated-zloc))
          (println (format "Updated unicode-versions in %s with sorted versions" script-file))
          true)
        (catch Exception e
          (println (format "Warning: Could not update %s: %s" script-file (.getMessage e)))
          false))
      (do
        (println (format "Warning: Cannot write to %s (not writable)" script-file))
        false))))

(defn calculate-download-summary
  "Pure function to calculate download summary from results"
  [results versions]
  (let [total-downloaded (reduce + (map :downloaded results))
        total-skipped    (reduce + (map :skipped results))]
    {:downloaded total-downloaded
     :skipped    total-skipped
     :versions   (count versions)
     :results    results}))

(defn print-download-summary
  "Print download summary if files were downloaded"
  [{:keys [downloaded versions]}]
  (when (pos? downloaded)
    (println (format "\n=== Summary ==="))
    (println (format "Downloaded %d files across %d versions" downloaded versions))))

(defn download-versions
  "Download Unicode files for a list of versions and return summary"
  [versions]
  (let [results (mapv download-unicode-files versions)
        summary (calculate-download-summary results versions)]
    (print-download-summary summary)
    summary))

(defn print-version-discovery-info
  "Print information about version discovery results"
  [{:keys [latest existing new]}]
  (println (format "Latest Unicode version: %s" (or latest "unknown")))
  (println (format "Existing versions: %s" (str/join ", " existing)))
  (if (seq new)
    (println (format "New versions found: %s" (str/join ", " new)))
    (println "No new versions found")))

(defn handle-new-flag
  "Handle the --new flag for dynamic version discovery"
  []
  (println "Discovering new Unicode versions...")
  (let [discovery         (discover-new-versions)
        {:keys [new all]} discovery]
    (print-version-discovery-info discovery)
    (if (seq new)
      (do
        (when (update-unicode-versions-in-file all)
          (println "Script updated with new versions"))
        (download-versions all))
      (download-versions unicode-versions))))

(defn handle-normal-operation
  "Handle normal download operation without --new flag"
  [args]
  (let [versions (if (empty? args)
                   unicode-versions
                   (if (= (first args) "all")
                     unicode-versions
                     args))]
    (download-versions versions)))

(defn -main [& args]
  (ensure-reference-files)

  (when (some #{"--python-refs"} args)
    (download-precis-python-refs))

  (if (some #{"--new"} args)
    (handle-new-flag)
    (handle-normal-operation args)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
