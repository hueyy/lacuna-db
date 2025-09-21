#!/usr/bin/env bb

(ns input.hearings.get-hearings
  (:require [clojure.math :as math]
            [babashka.pods :as pods]
            [cheshire.core :as json]
            [input.utils.general :as utils]
            [input.utils.date :as date]
            [input.hearings.populate-hearing-data :refer [populate-hearing-data]]
            [input.utils.log :as log])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter])
  (:gen-class))


(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require '[pod.retrogradeorbit.hickory.select :as s])

(def URL "https://www.judiciary.gov.sg/hearing-list/GetFilteredList")
(def JSON_FILE "data/hearings.json")

(def PAGE_SIZE 750)
(def MAX_DAYS 90)

;; (def COURTS {:fjc "Family Justice Courts"
;;              :suc "Supreme Court"
;;              :stc "State Courts"})

(defn- make-request-body
  ([] (make-request-body 0))
  ([page-number]
   {:model {:CurrentPage page-number
            :SelectedCourtTab ""
            :SearchKeywords ""
            :SearchKeywordsGrouping ""
            :SelectedCourt ""
            :SelectedLawFirms []
            :SelectedJudges []
            :SelectedHearingTypes []
            :SelectedStartDate (-> (date/get-current-date)
                                   (.minusDays 1)
                                   (date/to-iso-8601-with-tz))
            :SelectedEndDate (-> (date/get-current-date) (.plusDays MAX_DAYS) (date/to-iso-8601-with-tz))
            :SelectedPageSize (str PAGE_SIZE)
            :SelectedSortBy "0"}}))

(defn- get-hearing-list-page-raw
  [page-number]
  (log/debug "get-hearing-list-page-raw" page-number)
  (-> #(utils/curli-post-json URL (make-request-body page-number))
      (utils/retry-func 10 60)
      :listPartialView
      (utils/parse-html)))

(defn- get-hearing-type [html-el]
  (let [selection (s/select
                   (s/child (s/class "hearing-metadata")
                            (s/class "hearing-type"))
                   html-el)]
    (if (> (count selection) 0)
      (->> selection (first) (utils/get-el-content))
      nil)))

(defn- format-timestamp [timestamp]
  (.format DateTimeFormatter/ISO_LOCAL_DATE_TIME
           (LocalDateTime/parse timestamp
                                (DateTimeFormatter/ofPattern "dd MMM yyyy', 'h:mm a"))))

(defn- parse-hearing-element [html-el]
  (let [hearing-metadata-els
        (s/select (s/child (s/class "hearing-metadata")
                           (s/class "metadata-wrapper")
                           (s/class "metadata"))
                  html-el)
        timestamp (->> hearing-metadata-els
                       (first)
                       (utils/get-el-content)
                       (format-timestamp))
        hearing-item-wrapper (->> html-el
                                  (s/select (s/child (s/class "hearing-item-wrapper"))))]
    {:title (->> html-el :attrs :title)
     :link (->> html-el :attrs :href
                (str "https://www.judiciary.gov.sg"))
     :type (get-hearing-type html-el)
     :reference (if (> (count hearing-metadata-els) 1)
                  (->> hearing-metadata-els
                       (second)
                       (utils/get-el-content))
                  nil)
     :timestamp timestamp
     :venue (->> hearing-item-wrapper
                 (first)
                 (s/select (s/child (s/class "text")))
                 (first)
                 (utils/get-el-content))
     :coram (->> hearing-item-wrapper
                 (second)
                 (s/select (s/child (s/class "text")))
                 (first)
                 (utils/get-el-content))}))

(defn- parse-hearing-list-html
  [h-map]
  (->> h-map
       (s/select (s/child (s/and (s/class "list-item")
                                 (s/tag :a))))
       (pmap parse-hearing-element)))

(defn- get-pagination-status
  "Returns the number of additional pages there are"
  [h-map]
  (let [pagination-status (->> h-map
                               (s/select
                                (s/child (s/class "pagination-summary")))
                               (first)
                               (utils/get-el-content)
                               (re-find (re-pattern (str "(?i)Showing results 1-" PAGE_SIZE " of (\\d{3,})\\."))))]
    (log/debug (->> h-map
                    (s/select
                     (s/child (s/class "pagination-summary")))
                    (first)))
    (if (nil? pagination-status)
      0
      (-> pagination-status (last) (Integer.)
          (/ 500) (math/ceil) (int)))))

(defn get-hearing-list []
  (log/debug "Fetching hearing list...")
  (let [first-page-html (get-hearing-list-page-raw 0)
        additional-pages-count (get-pagination-status first-page-html)
        additional-pages (->> (range additional-pages-count)
                              (partition-all 2)
                              (mapcat (fn [chunk]
                                        (pmap #(get-hearing-list-page-raw (inc %)) chunk))))]
    (flatten (cons (parse-hearing-list-html first-page-html)
                   (pmap parse-hearing-list-html additional-pages)))))

(defn -main []
  (->> (get-hearing-list)
       (populate-hearing-data)
       (json/generate-string)
       (spit JSON_FILE)))
