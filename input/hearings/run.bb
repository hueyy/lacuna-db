#!/usr/bin/env bb

(ns input.hearings.run
  (:require [cheshire.core :as json]
            [input.hearings.get_hearings :refer [get-hearing-list]]
            [input.hearings.populate_hearing_data :refer [populate-hearing-data]]
            [babashka.fs :as fs]
            [clojure.string :as str]))

(def JSON_FILE (str/join fs/path-separator
                         ["data" "hearings.json"]))

(->> (get-hearing-list)
     (populate-hearing-data)
     (json/generate-string)
     (spit JSON_FILE))