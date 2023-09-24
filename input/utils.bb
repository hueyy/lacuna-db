(ns input.utils
  (:require [clojure.zip :as zip]
            [babashka.pods :as pods]
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

(defn get-el-content
  ([current output]
   (if (string? current)
     (str output current)
     (if (nil? (:content current))
       output
       (get-el-content
        (str/join "" (map #(get-el-content % output)
                          (:content current)))
        output))))
  ([el]
   (get-el-content el "")))

; Taken from https://github.com/clj-commons/hickory/blob/d721c9accd74b1618200347a0a1f05907441cbfd/src/cljc/hickory/select.cljc#L283
(defn find-in-text
  "Returns a function that takes a zip-loc argument and returns the zip-loc
   passed in if it has some text node in its contents that matches the regular
   expression. Note that this only applies to the direct text content of a node;
   nodes which have the given text in one of their child nodes will not be
   selected."
  [re]
  (fn [hzip-loc]
    (some #(re-find re %) (->> (zip/node hzip-loc)
                               :content
                               (filter string?)))))

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

(defn make-absolute-url [domain url]
  (if (str/starts-with? url "/")
    (str domain url)
    url))
