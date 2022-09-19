(ns zeta.enniu.crypto
  "加解密工具"
  (:require [zeta
             [db :as db]
             [opt :as zeta]
             [logging :as log]])
  (:import com.u51.codec.Codec))

(def ^:private codec
  (Codec/getInstance (zeta/of [:profile] "Production")))

(defn ^String decode [^String data]
  (try
    (.decode codec data)
    (catch Throwable e
      (log/warn "decode {} failed by {}" data (.getMessage e))
      data)))

(defn ^String decode-gjj [^String data]
  (try
    (.decodeGjj codec data)
    (catch Throwable e
      (log/warn "decode-gjj {} failed by {}" data (.getMessage e))
      data)))

(defn ^String encode-aes [^String data]
  (try
    (.encodeAes codec data)
    (catch Throwable e
      (log/warn "encode-aes {} failed by {}" data (.getMessage e))
      data)))

(defn ^String encode-des [^String data]
  (try
    (.encodeDes codec data)
    (catch Throwable e
      (log/warn "encode-des {} failed by {}" data (.getMessage e))
      data)))

(defn ^String md5 [^String data]
  (try
    (.encodeMd5 codec data)
    (catch Throwable e
      (log/warn "encode-md5 {} failed by {}" data (.getMessage e))
      data)))

(defn decode-in
  "解密hashmap内的多个字段"
  [ks m]
  (reduce
   (fn [hash key]
     (update hash key decode))
   m
   ks))

(db/def-plugin encoded [value]
  (if (string? value)
    (decode value)
    value))
