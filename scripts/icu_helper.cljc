(ns icu-helper
  (:import
   #?(:bb '()
      :clj [com.ibm.icu.text Normalizer2])))

#_{:clj-kondo/ignore [:uninitialized-var]}
(def NFKC
  #?(:bb nil
     :clj (Normalizer2/getNFKCInstance)))

#_{:clj-kondo/ignore [:unused-binding]}
(defn norm-nfkc [s]
  #?(:bb (java.text.Normalizer/normalize s java.text.Normalizer$Form/NFKC)
     :clj (.normalize NFKC s)))
