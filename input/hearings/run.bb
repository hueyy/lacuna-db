#!/usr/bin/env bb

(ns input.hearings.run
  (:require [cheshire.core :as json]
            [input.hearings.get_hearings :refer [get-hearing-list]]
            [input.hearings.populate_hearing_data :refer [populate-hearing-data]]))

(->> (get-hearing-list)
     (populate-hearing-data)
     (json/generate-string)
     (spit "hearings.json"))