#!/user/bin/env bb

(ns input.pdpc.undertakings
  (:require [babashka.pods :as pods]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [input.utils.general :as utils]
            [input.utils.date :as date]
            [input.utils.pdf :as pdf]
            [cheshire.core :as json]
            [input.utils.log :as log]))

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require '[pod.retrogradeorbit.hickory.select :as s]
         '[pod.retrogradeorbit.bootleg.utils :as butils])

(def DOMAIN "https://www.pdpc.gov.sg")
(def URL (str DOMAIN "/Undertakings"))
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
                     (date/parse-short-date)
                     (date/to-iso-8601-date))}))

(defn- parse-undertakings-html [h-map]
  (->> h-map
       (s/select (s/descendant (s/class "rte")
                               (s/tag :table)))
       (first)
       (s/select (s/descendant (s/tag :tbody)
                               (s/tag :tr)))
       (pmap parse-row)))

(defn- parse-undertaking-detail-html [h-map]
  (let [description (-> (s/select (s/and (s/class "rte")
                                         (s/tag :div))
                                  h-map)
                        (first))
        pdf-url (->> description
                     (s/select (s/and (s/tag :a)
                                      (utils/find-in-text #"(?i)^(\s| )*here(\s| )*$")))
                     (first)
                     :attrs
                     :href
                     (utils/make-absolute-url DOMAIN))]
    {:raw-description (-> description (butils/convert-to :html))
     :description (-> description
                      (utils/get-el-content)
                      (str/trim))
     :pdf-url pdf-url
     :pdf-content (pdf/get-content-from-url pdf-url
                                            :ocr? true
                                            :ocr-options {:skip-strategy :skip-text})}))

(defn- get-undertaking-detail [url]
  (log/debug "Fetching undertaking detail: " url)
  (-> (utils/retry-func #(utils/curli url) 5 60)
      (utils/parse-html)
      (parse-undertaking-detail-html)))

(defn- get-undertakings [prev-hash]
  (let [h-map (-> (utils/retry-func #(utils/curli URL))
                  (utils/parse-html))
        cur-hash (hash-unordered-coll h-map)]
    (log/debug "Fetched PDPC undertakings")
    (if (= cur-hash prev-hash)
      (do
        (log/debug "Same hash, do nothing")
        nil)
      (let [undertakings (parse-undertakings-html h-map)]
        (log/debug "Different hash, parse HTML")
        (spit HASH_FILE cur-hash)
        (map #(try
                (->> % :url
                     (get-undertaking-detail)
                     (merge %))
                (catch Exception e
                  (log/error (str "Caught exception: "
                                  (.getMessage e)))))
             undertakings)))))

(defn run []
  (let [current-hash (if (fs/exists? HASH_FILE)
                       (slurp HASH_FILE)
                       nil)
        current-undertakings (get-undertakings current-hash)]
    (when (not (nil? current-undertakings))
      (->> current-undertakings
           (json/generate-string)
           (spit JSON_FILE)))))

(defn -main []
  (run))