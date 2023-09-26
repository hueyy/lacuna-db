#!/user/bin/env bb

(ns input.pdpc.undertakings
  (:require [babashka.curl :as curl]
            [babashka.pods :as pods]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [input.utils :as utils]
            [babashka.process :refer [sh]]
            [cheshire.core :as json]))

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require '[pod.retrogradeorbit.hickory.select :as s]
         '[pod.retrogradeorbit.bootleg.utils :as butils])

(def DOMAIN "https://www.pdpc.gov.sg")
(def URL (str DOMAIN "/Undertakings"))
(def PDF_FILENAME "undertaking.pdf")
(def JSON_FILE "data/pdpc-undertakings.json")
(def HASH_FILE "data/pdpc-undertakings.hash")

(defn- parse-row [row]
  (let [tds (s/select (s/child (s/tag :td)) row)
        link (->> tds (second)
                  (s/select (s/child (s/tag :a)))
                  (first))]
    {:id (->> tds (first)
              (s/select (s/child (s/tag :strong)))
              (first)
              (utils/get-el-content)
              (utils/clean-string))
     :organisation (->> link
                        (utils/get-el-content)
                        (utils/clean-string))
     :url (->> link :attrs
               :href
               (utils/make-absolute-url DOMAIN))
     :timestamp (->> (nth tds 2)
                     (s/select (s/child (s/tag :strong)))
                     (first)
                     (utils/get-el-content)
                     (utils/clean-string)
                     (utils/format-short-date))}))

(defn- parse-undertakings-html [h-map]
  (->> h-map
       (s/select (s/descendant (s/class "rte")
                               (s/tag :table)))
       (first)
       (s/select (s/descendant (s/tag :tbody)
                               (s/tag :tr)))
       (map parse-row)))

(defn handle-pdf [url]
  (sh "wget" "--output-document" PDF_FILENAME url)
  (let [pdf-content (-> (sh ["pdftotext" PDF_FILENAME "-"])
                        :out
                        slurp)]
    (sh "rm" PDF_FILENAME)
    pdf-content))

(defn parse-undertaking-detail-html [h-map]
  (let [description (-> (s/select (s/and (s/class "rte")
                                         (s/tag :div))
                                  h-map)
                        (first))
        pdf-url (->> description
                     (s/select (s/and (s/tag :a)
                                      (utils/find-in-text #"^\s*here\s*$")))
                     (first)
                     :attrs
                     :href
                     (utils/make-absolute-url DOMAIN))]
    {:raw-description (-> description (butils/convert-to :html))
     :description (-> description
                      (utils/get-el-content)
                      (str/trim))
     :pdf-url pdf-url
    ;;  :pdf-content (handle-pdf pdf-url)
     }))

(defn get-undertaking-detail [url]
  (-> (curl/get url)
      :body
      (utils/parse-html)
      (parse-undertaking-detail-html)))

(defn- get-undertakings [prev-hash]
  (let [h-map (-> (curl/get URL)
                  :body
                  (utils/parse-html))
        cur-hash (hash-unordered-coll h-map)]
    (if (= cur-hash prev-hash)
      nil
      (let [undertakings (parse-undertakings-html h-map)]
        (spit HASH_FILE cur-hash)
        (map #(try
                (Thread/sleep 5000)
                (->> % :url
                     (get-undertaking-detail)
                     (merge %))
                (catch Exception e
                  (println (str "Caught exception: "
                                (.getMessage e)))))
             undertakings)))))

(defn- run []
  (let [current-hash (if (fs/exists? HASH_FILE)
                       (slurp HASH_FILE)
                       nil)
        current-undertakings (get-undertakings current-hash)]
    (when (not (nil? current-undertakings))
      (->> current-undertakings
           (json/generate-string)
           (spit JSON_FILE)))))
