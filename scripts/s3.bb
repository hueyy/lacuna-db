(ns scripts.s3
  (:require [babashka.process :refer [sh shell]]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def BUCKET_NAME "LawArchive")
(def DB_FOLDER "data")
(def DB_FILE "data.db")
(def DB_PATH (str/join "/" [DB_FOLDER DB_FILE]))

(defn get-db-file-id []
  (-> (sh (str "b2 ls --long --json --recursive --withWildcard"
               BUCKET_NAME DB_PATH))
      :out
      (json/parse-string)
      (first)
      :fileId))

(defn sync-db-down []
  (sh (str "poetry run b2 sync "
           "b2://" BUCKET_NAME DB_PATH
           " "
           DB_PATH)))

(defn sync-db-up []
  (shell (str "poetry run b2 sync "
              "--includeRegex " DB_FILE " "
              "--excludeRegex . "
              DB_FOLDER
              " "
              "b2://" BUCKET_NAME "/" DB_FOLDER)))