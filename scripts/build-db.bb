#!/usr/bin/env bb

(require '[babashka.process :refer [sh process]]
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

(defn generate-db [namespace input-file]
  (let [result (sh "poetry run git-history file"
                   "--start-at" "96398149e899fe720a936dbcd6864f4b4c99b340"
                   "--skip" (get-ignored-commits)
                   "--namespace" namespace
                   DB_FILE input-file)]
    (if (not (= 0 (:exit result)))
      (:err result)
      (:out result))))

(defn run-sql-on-db [f]
  (let [stream (-> (process ["cat" f]) :out)]
    @(process ["sqlite3" DB_FILE] {:in stream
                                   :out :inherit})))

(defn run []
  (generate-db "hearings" "hearings.json")
  (generate-db "sc" "sc.json"))

(run)