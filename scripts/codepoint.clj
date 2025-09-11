#!/usr/bin/env bb
(ns codepoint
  (:require [generate :as g]
            [clojure.pprint :as pprint]
            [clojure.string :as str]))

(defn parse-version-spec
  "Parse version specification: single version, comma-separated, or range"
  [version-spec]
  (cond
    (str/includes? version-spec ",")
    (map str/trim (str/split version-spec #","))

    (str/includes? version-spec "-")
    (let [[start end] (map str/trim (str/split version-spec #"-"))
          start-major (Integer/parseInt (first (str/split start #"\.")))
          end-major (Integer/parseInt (first (str/split end #"\.")))
          start-minor (if (str/includes? start ".")
                        (Integer/parseInt (second (str/split start #"\.")))
                        0)
          end-minor (if (str/includes? end ".")
                      (Integer/parseInt (second (str/split end #"\.")))
                      0)]
      (for [major (range start-major (inc end-major))
            minor (range (if (= major start-major) start-minor 0)
                         (inc (if (= major end-major) end-minor 0)))
            :let [version (str major "." minor ".0")]
            :when (.exists (java.io.File. (str "createtables/" version)))]
        version))

    :else
    [(if (str/includes? version-spec ".") version-spec (str version-spec ".0.0"))]))

(defn load-unicode-data-for-versions
  "Load Unicode data for multiple versions"
  [versions cp]
  (reduce (fn [acc version]
            (let [unicode-data (g/parse-unicode-data (str "createtables/" version "/UnicodeData.txt"))
                  derived-props (g/parse-derived-core-properties (str "createtables/" version "/DerivedCoreProperties.txt"))]
              (assoc acc version {:unicode-data unicode-data
                                  :derived-props derived-props
                                  :entry (get unicode-data cp)})))
          {} versions))

(defn test-codepoint
  "Test and diagnose PRECIS classification for a given codepoint across Unicode versions"
  [cp-hex versions]
  (let [cp (g/parse-hex cp-hex)
        version-data (load-unicode-data-for-versions versions cp)]

    (printf "=== CODEPOINT U+%s (%d) ===\n" cp-hex cp)

    (println "\n--- UNICODE DATA ----------------")
    ;; Show data from the first version for context
    (let [first-version (first versions)
          first-entry (get-in version-data [first-version :entry])]
      (if first-entry
        (do
          (println "Name            :" (:name first-entry))
          (println "General Category:" (:general-category first-entry))
          (println "Decomposition   :" (or (:decomposition first-entry) "(none)")))
        (println "Character may be unassigned in some versions")))

    (println "\n--- PRECIS ALGORITHM STEPS MATRIX ------")
    ;; Create matrix of versions (vertical) vs steps (horizontal)
    (let [step-functions [{:step "1 exceptions-value" :fn #(g/exceptions-value cp)}
                          {:step "2 assigned?" :fn (fn [data _] (contains? (:unicode-data data) cp))}
                          {:step "3 ascii7?" :fn #(g/ascii7? cp)}
                          {:step "4 join-control?" :fn #(g/join-control? cp)}
                          {:step "5 old-hangul-jamo?" :fn #(g/old-hangul-jamo? cp)}
                          {:step "6 precis-ignorable?" :fn (fn [data _] (g/precis-ignorable? (:derived-props data) cp))}
                          {:step "7 control?" :fn (fn [data _] (g/control? (:unicode-data data) cp))}
                          {:step "8 has-compat?" :fn (fn [data _] (g/has-compat? (:unicode-data data) cp))}
                          {:step "9 letter-digits?" :fn (fn [data _] (g/letter-digits? (:unicode-data data) cp))}
                          {:step "10 other-letter-digits?" :fn (fn [data _] (g/other-letter-digits? (:unicode-data data) cp))}
                          {:step "11 spaces?" :fn (fn [data _] (g/spaces? (:unicode-data data) cp))}
                          {:step "12 symbols?" :fn (fn [data _] (g/symbols? (:unicode-data data) cp))}
                          {:step "13 punctuation?" :fn (fn [data _] (g/punctuation? (:unicode-data data) cp))}]
          matrix-data (for [version versions]
                        (let [data (get version-data version)
                              assigned? (contains? (:unicode-data data) cp)
                              row-data (merge {:version version}
                                              (if assigned?
                                                (into {} (map (fn [{:keys [step fn]}]
                                                                (let [result (if (#{"1 exceptions-value" "3 ascii7?" "4 join-control?" "5 old-hangul-jamo?"} step)
                                                                               (fn)
                                                                               (fn data version))]
                                                                  [step result]))
                                                              step-functions))
                                                (into {} (map (fn [{:keys [step]}]
                                                                [step (if (#{"1 exceptions-value" "2 assigned?"} step)
                                                                        (if (= step "2 assigned?") false (g/exceptions-value cp))
                                                                        "N/A")])
                                                              step-functions))))]
                          row-data))]
      (pprint/print-table (cons :version (map :step step-functions)) matrix-data))

    (println "\n--- RESULTS ACROSS VERSIONS -----")
    (let [iana-exceptions (g/load-iana-exceptions)]
      (doseq [version versions]
        (let [data (get version-data version)
              unicode-data (:unicode-data data)
              derived-props (:derived-props data)
              result (if (contains? unicode-data cp)
                       (g/derive-precis-property unicode-data derived-props cp)
                       :unassigned)
              iana-result (if (and (= version "6.3.0") (contains? unicode-data cp) (contains? iana-exceptions cp))
                            (get iana-exceptions cp)
                            nil)]
          (printf "Unicode %-6s: %s" version result)
          (when iana-result
            (printf " (IANA override: %s)" iana-result))
          (println))))

    (when (and (<= 0x180B cp 0x180F) (not= cp 0x180E))
      (println "\n--- RELATED FVS CHARACTERS ------")
      ;; Show FVS characters for the first version only
      (let [first-version (first versions)
            first-data (get version-data first-version)
            unicode-data (:unicode-data first-data)
            derived-props (:derived-props first-data)]
        (doseq [fvs-cp [0x180B 0x180C 0x180D 0x180F]]
          (let [fvs-entry (get unicode-data fvs-cp)
                fvs-result (when fvs-entry
                             (g/derive-precis-property unicode-data derived-props fvs-cp))]
            (printf "U+%04X (%s): %s -> %s\n"
                    fvs-cp
                    (:general-category fvs-entry "N/A")
                    (:name fvs-entry "NOT FOUND")
                    (or fvs-result "unassigned"))))))

    (println)))

(defn -main [& args]
  (let [parsed-args (loop [remaining args
                           cp-hex nil
                           versions nil]
                      (cond
                        (empty? remaining)
                        {:cp-hex cp-hex :versions versions}

                        (= (first remaining) "--unicode")
                        (recur (drop 2 remaining) cp-hex (parse-version-spec (second remaining)))

                        (nil? cp-hex)
                        (recur (rest remaining) (first remaining) versions)

                        :else
                        (recur (rest remaining) cp-hex versions)))]
    (cond
      (nil? (:cp-hex parsed-args))
      (do
        (println "Usage: bb codepoint <codepoint-hex> [--unicode <versions>]")
        (println "Examples:")
        (println "  bb codepoint 180F                    # Test with all Unicode versions (default)")
        (println "  bb codepoint 180F --unicode 14       # Test with Unicode 14.0.0")
        (println "  bb codepoint 180F --unicode 6.3.0    # Test with Unicode 6.3.0")
        (println "  bb codepoint 180F --unicode 11,12,13 # Test with multiple versions")
        (println "  bb codepoint 180F --unicode 6-17     # Test with version range"))

      :else
      (let [versions (or (:versions parsed-args) g/unicode-versions)]
        (test-codepoint (:cp-hex parsed-args) versions)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
