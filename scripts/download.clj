#!/usr/bin/env bb
(ns download)
(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[clojure.set :as set]
         '[clojure.data.csv :as csv])

(def scratch-dir "reference/scratch")
(def data-dir "data")
(def unicode-versions ["6.3.0" "7.0.0" "8.0.0" "9.0.0" "10.0.0" "11.0.0"
                       "12.0.0" "13.0.0" "14.0.0" "15.0.0" "15.1.0" "16.0.0" "17.0.0"])

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
  (with-open [in (io/input-stream url)
              out (io/output-stream local-path)]
    (io/copy in out)))

(defn download-unicode-files
  "Download Unicode data files for a specific version"
  [version]
  (let [base-url (str "https://www.unicode.org/Public/" version "/ucd/")
        version-dir (str data-dir "/" version)
        downloaded (atom 0)
        skipped (atom 0)
        first-download? (atom true)]
    (.mkdirs (io/file version-dir))
    (doseq [filename unicode-files]
      (let [url (str base-url filename)
            local-path (str version-dir "/" filename)
            file (io/file local-path)]
        (if (.exists file)
          (swap! skipped inc)
          (try
            (when @first-download?
              (println (format "\nUnicode %s:" version))
              (reset! first-download? false))
            (download-file url local-path filename)
            (swap! downloaded inc)
            (catch Exception e
              (println (format "  Warning: Could not download %s: %s" filename (.getMessage e))))))))
    {:version version :downloaded @downloaded :skipped @skipped}))

(defn ensure-reference-files
  "Download required reference files if they don't exist"
  []
  (let [files [{:url "https://www.iana.org/assignments/precis-tables-6.3.0/precis-tables-6.3.0.csv"
                :local (str scratch-dir "/precis-tables-6.3.0.csv")}
               {:url "https://www.unicode.org/Public/idna/idna2008derived/Idna2008-6.3.0.txt"
                :local (str scratch-dir "/Idna2008-6.3.0.txt")}
               {:url "https://www.iana.org/assignments/idna-tables-6.3.0/idna-tables-context.csv"
                :local (str scratch-dir "/idna-tables-context.csv")}]]
    (doseq [{:keys [url local]} files]
      (let [file (io/file local)
            filename (.getName file)]
        (when-not (.exists file)
          (try
            (println (format "Downloading %s..." filename))
            (download-file url local filename)
            (catch Exception e
              (println (format "Warning: Could not download %s: %s" url (.getMessage e))))))))))

(defn -main [& args]
  ;; Download reference files for verification
  (ensure-reference-files)

  ;; Download Unicode data files based on arguments
  (let [versions (if (empty? args)
                   ;; Default to all versions when no args provided
                   unicode-versions
                   (if (= (first args) "all")
                     unicode-versions
                     args))
        results (atom [])]
    (doseq [version versions]
      (swap! results conj (download-unicode-files version)))

    ;; Only print summary if something was downloaded
    (let [total-downloaded (reduce + (map :downloaded @results))
          total-skipped (reduce + (map :skipped @results))]
      (when (pos? total-downloaded)
        (println (format "\n=== Summary ==="))
        (println (format "Downloaded %d files across %d versions" total-downloaded (count versions)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
