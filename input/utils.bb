(ns input.utils
  (:require [babashka.pods :as pods]
            [clojure.string :as str]))

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