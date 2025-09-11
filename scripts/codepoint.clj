#!/usr/bin/env bb
(ns codepoint
  (:require [common :as common]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(def unicode-versions ["6.3.0" "7.0.0" "8.0.0" "9.0.0" "10.0.0" "11.0.0" "12.0.0" "13.0.0" "14.0.0" "15.0.0" "16.0.0" "17.0.0"])

(defn load-unicode-data-for-versions
  "Load Unicode data for multiple versions"
  [versions cp]
  (reduce (fn [acc version]
            (let [unicode-data (common/parse-unicode-data (str "data/" version "/UnicodeData.txt"))
                  derived-props (common/parse-derived-core-properties (str "data/" version "/DerivedCoreProperties.txt"))]
              (assoc acc version {:unicode-data unicode-data
                                  :derived-props derived-props
                                  :entry (get unicode-data cp)})))
          {} versions))

(defn test-codepoint
  "Test and diagnose PRECIS classification for a given codepoint across Unicode versions"
  [cp-hex versions]
  (let [cp           (common/parse-hex cp-hex)
        version-data (load-unicode-data-for-versions versions cp)]

    (printf "=== CODEPOINT U+%s (%d) ===\n" cp-hex cp)

    (let [first-version (first versions)
          first-entry   (get-in version-data [first-version :entry])]
      (if first-entry
        (do
          (println "Name            :" (:name first-entry))
          (println "General Category:" (:general-category first-entry))
          (println "Decomposition   :" (or (:decomposition first-entry) "(none)")))
        (println "Character may be unassigned in some versions")))

    (let [all-step-functions [{:step "1 exceptions-value" :fn #(common/exceptions-value cp) :context-free true}
                              {:step "2 assigned?" :fn (fn [data _] (contains? (:unicode-data data) cp)) :context-free false}
                              {:step "3 ascii7?" :fn #(common/ascii7? cp) :context-free true}
                              {:step "4 join-control?" :fn #(common/join-control? cp) :context-free true}
                              {:step "5 old-hangul-jamo?" :fn #(common/old-hangul-jamo? cp) :context-free true}
                              {:step "6 precis-ignorable?" :fn (fn [data _] (common/precis-ignorable? (:derived-props data) cp)) :context-free false}
                              {:step "7 control?" :fn (fn [data _] (common/control? (:unicode-data data) cp)) :context-free false}
                              {:step "8 has-compat?" :fn (fn [data _] (common/has-compat? (:unicode-data data) cp)) :context-free false}
                              {:step "9 letter-digits?" :fn (fn [data _] (common/letter-digits? (:unicode-data data) cp)) :context-free false}
                              {:step "10 other-letter-digits?" :fn (fn [data _] (common/other-letter-digits? (:unicode-data data) cp)) :context-free false}
                              {:step "11 spaces?" :fn (fn [data _] (common/spaces? (:unicode-data data) cp)) :context-free false}
                              {:step "12 symbols?" :fn (fn [data _] (common/symbols? (:unicode-data data) cp)) :context-free false}
                              {:step "13 punctuation?" :fn (fn [data _] (common/punctuation? (:unicode-data data) cp)) :context-free false}]

          ;; Helper function to create matrix data
          create-matrix (fn [step-functions]
                          (for [version versions]
                            (let [data      (get version-data version)
                                  assigned? (contains? (:unicode-data data) cp)]
                              (merge {:version version}
                                     (if assigned?
                                       (into {} (map (fn [{:keys [step fn context-free]}]
                                                       (let [result (if context-free (fn) (fn data version))]
                                                         [step result]))
                                                     step-functions))
                                       (into {} (map (fn [{:keys [step]}]
                                                       [step (cond
                                                               (= step "1 exceptions-value") (common/exceptions-value cp)
                                                               (= step "2 assigned?")        false
                                                               :else                         "N/A")])
                                                     step-functions)))))))

          ;; Helper function to print matrix
          print-matrix (fn [title step-functions]
                         (println (str "\n--- " title " ------"))
                         (let [matrix-data (create-matrix step-functions)]
                           (pprint/print-table (cons :version (map :step step-functions)) matrix-data)))]

      ;; Print both matrices
      (print-matrix "PRECIS ALGORITHM STEPS MATRIX (Steps 1-6)" (take 6 all-step-functions))
      (print-matrix "PRECIS ALGORITHM STEPS MATRIX (Steps 7-13)" (drop 6 all-step-functions)))

    (println "\n--- PRECIS DERIVED PROPERTY ACROSS VERSIONS -----")
    (let [iana-exceptions (common/load-iana-exceptions-cached)]
      (doseq [version versions]
        (let [data             (get version-data version)
              unicode-data     (:unicode-data data)
              derived-props    (:derived-props data)
              entry            (get unicode-data cp)
              general-category (if entry (:general-category entry) "unassigned")
              result           (common/derive-precis-property unicode-data derived-props cp version)
              iana-result      (if (and (= version "6.3.0") (contains? unicode-data cp) (contains? iana-exceptions cp))
                                 (get iana-exceptions cp)
                                 nil)]
          (printf "Unicode %-6s: %-12s (General Category: %s)" version result general-category)
          (when iana-result
            (printf " [IANA override: %s]" iana-result))
          (println))))))

(defn -main [& args]
  (let [parsed-args (loop [remaining args
                           cp-hex nil
                           versions nil]
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
