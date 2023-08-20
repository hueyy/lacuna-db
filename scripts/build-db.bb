#!/usr/bin/env bb

(require '[babashka.process :refer [shell sh process]]
         '[babashka.fs :as fs]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(def MERGESTAT_BINARY "mergestat")
(def DB_FILE "data.db")

(defn download-mergestat []
  (sh "wget" "https://github.com/mergestat/mergestat-lite/releases/download/v0.6.1/mergestat-linux-amd64.tar.gz")
  (sh "tar -xvf" "mergestat-linux-amd64.tar.gz")
  (sh "rm" "mergestat-linux-amd64.tar.gz" "libmergestat.so")
  (sh "chmod +x" MERGESTAT_BINARY))

(defn get-ignored-commits []
  (when (not (fs/exists? MERGESTAT_BINARY))
    (download-mergestat))
  (->> (-> (sh "mergestat -f json" "SELECT hash FROM commits WHERE committer_email IS NOT 'actions@users.noreply.github.com'")
           :out
           (json/parse-string true))
       (map :hash)
       (str/join " ")))

(defn generate-db
  ([namespace input-file]
   (generate-db namespace input-file nil nil))
  ([namespace input-file start-at]
   (generate-db namespace input-file start-at nil))
  ([namespace input-file start-at skip]
   (-> (str "poetry run git-history file"
            " " DB_FILE
            " " input-file
            " --namespace " namespace
            (if (nil? start-at) "" (str " --start-at " start-at))
            (if (nil? skip) "" (str " --skip " (str "'" skip "'"))))
       (shell))))

(defn- run-sql-on-db [f]
  (let [stream (-> (process ["cat" f]) :out)]
    @(process ["sqlite3" DB_FILE] {:in stream
                                   :out :inherit})))

;; (defn add-table-to-db [table from-db to-db]
;;   (let))

(defn run []
  (generate-db "hearings" "hearings.json" "96398149e899fe720a936dbcd6864f4b4c99b340" (get-ignored-commits))
  (generate-db "sc" "sc.json" "c8ebb498cd605d5e15c366972aa9e829e13b370a"))

(run)