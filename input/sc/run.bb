#!/usr/bin/env bb

(ns input.sc.run
  (:require [babashka.curl :as curl]
            [babashka.pods :as pods]
            [clojure.string :as str]
            [input.utils :as utils]
            [cheshire.core :as json])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter])
  (:gen-class))

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require '[pod.retrogradeorbit.hickory.select :as s])

(def URL "https://www.sal.org.sg/Services/Appointments/Senior-Counsel/Directory")

(def titles ["Mr" "Ms" "Mrs"
             "Professor" "Dr"
             "Attorney-General" "Deputy Attorney-General" "Solicitor-General"
             "Chief Justice" "Justice" "Judicial Commissioner"
             "Deputy Presiding Judge"])

(def name-regex (re-pattern (str "(" (str/join "|" titles) ") "
                                 "([aA-zZ -]+)"
                                 "(, Judge of Appeal)?"
                                 "( \\(honoris causa\\))?"
                                 "(\\*)?$")))

(defn- format-timestamp [timestamp]
  (if (str/blank? timestamp)
    nil
    (.format DateTimeFormatter/ISO_LOCAL_DATE
             (LocalDate/parse timestamp
                              (DateTimeFormatter/ofPattern "d MMM yyyy")))))

(defn- get-page []
  (-> (curl/get URL)
      :body
      (utils/parse-html)))

(defn- parse-name
  "returns map with various keys"
  [raw-name]
  (let [matches (re-matches name-regex raw-name)]
    {:title (second matches)
     :name (nth matches 2)
     :judge-of-appeal (-> (nth matches 3) nil? not)
     :honoris-causa (-> (nth matches 4) nil? not)
     :deceased (-> (nth matches 5) nil? not)}))

(defn- get-span-content
  "gets the text content of the innermost <span>"
  [td]
  (-> (->> td
           (s/select (s/descendant (s/or
                                    (s/tag :span)
                                    (s/tag :font))))
           (last)
           (utils/get-el-content))
      (str/replace #"[ \s]" " ") ; normalise weird whitespace characters
      ))

(defn- parse-row [row]
  (let [tds (s/select (s/child (s/tag :td)) row)
        parsed-name (->> tds (second)
                         (get-span-content)
                         (parse-name))]
    (merge parsed-name
           {:organisation (-> (nth tds 2)
                              (get-span-content)
                              (str/trim)
                              (#(if-not (str/blank? %) % nil)))
            :appointment-provision (-> (nth tds 3)
                                       (get-span-content))
            :appointment-date (-> (nth tds 4)
                                  (get-span-content)
                                  (format-timestamp))})))

(defn- parse-page [h-map]
  (->> (s/select (s/descendant (s/tag :table)
                               (s/tag :tbody)
                               (s/tag :tr))
                 h-map)
       (drop 1)
       (drop-last 6)
       (map parse-row)))

(defn get-data []
  (-> (get-page)
      (parse-page)))

(->> (get-data)
     (json/generate-string)
     (spit "sc.json"))
