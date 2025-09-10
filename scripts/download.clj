#!/usr/bin/env bb
(ns download)
(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[clojure.set :as set]
         '[clojure.data.csv :as csv])

(def scratch-dir "scratch")

(defn download-file
  "Download a file from URL to local path"
  [url local-path]
  (println (format "Downloading %s to %s..." url local-path))
  (.mkdirs (.getParentFile (io/file local-path)))
  (with-open [in (io/input-stream url)
              out (io/output-stream local-path)]
    (io/copy in out))
  (println "Download complete."))

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
      (let [file (io/file local)]
        (when-not (.exists file)
          (try
            (download-file url local)
            (catch Exception e
              (println (format "Warning: Could not download %s: %s" url (.getMessage e))))))))))

(defn -main [& args]
  (ensure-reference-files))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
