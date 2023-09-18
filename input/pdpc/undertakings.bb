#!/user/bin/env bb

(ns input.pdpc.undertakings
  (:require [babashka.curl :as curl]
            [babashka.pods :as pods]
            [clojure.string :as str]
            [input.utils :as utils]))

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require '[pod.retrogradeorbit.hickory.select :as s])

(def URL "https://www.pdpc.gov.sg/Undertakings")

(defn- parse-row [row]
  (let [tds (s/select (s/child (s/tag :td)) row)
        link (->> tds (second)
                  (s/select (s/child (s/tag :strong)
                                     (s/tag :a)))
                  (first))]
    {:id (->> tds (first)
              (s/select (s/child (s/tag :strong)))
              (first)
              (utils/get-el-content)
              (str/trim))
     :organisation (->> link
                        (utils/get-el-content)
                        (str/trim))
     :url (->> link :attrs :href)
     :timestamp (->> (nth tds 2)
                     (s/select (s/child (s/tag :strong)))
                     (first)
                     (utils/get-el-content)
                     (str/trim))}))

(defn- parse-undertakings-html [h-map]
  (->> h-map
       (s/select (s/descendant (s/class "rte")
                               (s/tag :table)))
       (first)
       (s/select (s/descendant (s/tag :tbody)
                               (s/tag :tr)))
       (map parse-row)))

(defn- get-undertakings []
  (-> (curl/get URL)
      :body
      (utils/parse-html)
      (parse-undertakings-html)))