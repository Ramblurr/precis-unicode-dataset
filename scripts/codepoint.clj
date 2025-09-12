#!/usr/bin/env bb
(ns codepoint
  (:require
   [clojure.pprint :as pprint]
   [common :as common]))

(def unicode-versions ["6.3.0" "7.0.0" "8.0.0" "9.0.0" "10.0.0" "11.0.0" "12.0.0" "13.0.0" "14.0.0" "15.0.0" "16.0.0" "17.0.0"])

(defn load-unicode-data-for-versions
  "Load Unicode data for multiple versions"
  [versions cp]
  (reduce (fn [acc version]
            (let [unicode-data  (common/parse-unicode-data (str "data/" version "/UnicodeData.txt"))
                  derived-props (common/parse-derived-core-properties (str "data/" version "/DerivedCoreProperties.txt"))]
              (assoc acc version {:unicode-data  unicode-data
                                  :derived-props derived-props
                                  :entry         (get unicode-data cp)})))
          {} versions))

(defn test-codepoint
  "Test and diagnose PRECIS classification for a given codepoint across Unicode versions"
  [cp-hex versions]
  (println "Loading unicode data")
  (let [cp           (common/parse-hex cp-hex)
        version-data (load-unicode-data-for-versions versions cp)]

    (println "━━━ PRECIS DERIVED PROPERTY ACROSS VERSIONS ━━━")
    (let [table-data (for [version versions]
                       (let [data             (get version-data version)
                             unicode-data     (:unicode-data data)
                             derived-props    (:derived-props data)
                             entry            (get unicode-data cp)
                             general-category (if entry (:general-category entry) "unassigned")
                             [property reason] (common/derive-precis-property unicode-data derived-props cp)]
                         {:version version
                          :property (name property)
                          :reason (name reason)
                          :general-category general-category}))]
      (pprint/print-table [:version :property :reason :general-category] table-data))))

(defn -main [& args]
  (let [parsed-args (loop [remaining args
                           cp-hex    nil
                           versions  nil]
                      (cond
                        (empty? remaining)
                        {:cp-hex cp-hex :versions versions}

                        (nil? cp-hex)
                        (recur (rest remaining) (first remaining) versions)

                        :else
                        (recur (rest remaining) cp-hex versions)))]
    (cond
      (nil? (:cp-hex parsed-args))
      (do
        (println "Usage: bb codepoint <codepoint-hex>")
        (println "Examples:")
        (println "  bb codepoint 180F"))

      :else
      (let [versions (or (:versions parsed-args) unicode-versions)]
        (test-codepoint (:cp-hex parsed-args) versions)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
