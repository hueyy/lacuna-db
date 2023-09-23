(ns input.utils
  (:require [babashka.pods :as pods]
            [clojure.string :as str])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter])
  (:gen-class))

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require '[pod.retrogradeorbit.bootleg.utils :as bootleg])

(defn parse-html
  "takes HTML and returns hickory map"
  [html]
  (-> (str "<html>" html "</html>")
      (bootleg/convert-to  :hickory)))

(defn get-el-content [el]
  (->> el :content
       (filter string?)
       (str/join "")))

(defn normalise-whitespace [input-str]
  (str/replace input-str #"[ \s]" " "))

(defn clean-string [input-str]
  (-> input-str
      (normalise-whitespace)
      (str/trim)))

(defn fix-month-names
  "Replaces non-standard month names in short dates, e.g. Sept instead of Sep, June instead of Jun, etc"
  [input-date]
  (-> input-date
      (str/replace #"\bSept\b" "Sep")
      (str/replace #"\bJune\b" "Jun")
      (str/replace #"\bJuly\b" "Jul")))

(defn format-short-date [input-date]
  (if (str/blank? input-date)
    nil
    (.format DateTimeFormatter/ISO_LOCAL_DATE
             (-> input-date
                 (fix-month-names)
                 (LocalDate/parse (DateTimeFormatter/ofPattern "d MMM yyyy"))))))