(ns scripts.build-db
  (:require [babashka.process :refer [shell]]
            [taoensso.timbre :as timbre]
            [scripts.utils :as utils]
            [scripts.computed-columns :refer [add-computed-columns]]))

(def DB_FILE "data/data.db")

(defn use-optional-argument [value name]
  (if (nil? value) "" (str " " name " '" value "'")))

(defn generate-db
  ([namespace input-file]
   (generate-db namespace input-file nil))
  ([namespace input-file & {:keys [id
                                   start-at
                                   convert
                                   ignore]
                            :or {id nil
                                 start-at nil
                                 convert nil
                                 ignore nil}}]
   (-> (str "poetry run git-history file"
            " " DB_FILE
            " " input-file
            " --namespace " "'" namespace "'"
            (use-optional-argument start-at "--start-at")
            (use-optional-argument convert "--convert")
            (use-optional-argument ignore "--ignore")
            (use-optional-argument id "--id"))
       (#(utils/try-ignore-error (shell %))))))

(defn setup-fts [table fields]
  (try
    (apply shell (concat ["poetry" "run"
                          "sqlite-utils" "enable-fts"
                          DB_FILE table]
                         fields))
    (catch Exception e
      (timbre/error e))))

(def HEARINGS_JSON "data/hearings.json")
(def SC_JSON "data/sc.json")
(def PDPC_UNDERTAKINGS_JSON "data/pdpc-undertakings.json")
(def PDPC_DECISIONS_JSON "data/pdpc-decisions.json")
(def LSS_DT_REPORTS_JSON "data/lss-dt-reports.json")
(def STC_JUDGMENTS_JSON "data/stc-judgments.json")
(def FC_JUDGMENTS_JSON "data/fc-judgments.json")
(def SAL_SPECIALISTS_JSON "data/sal-specialists.json")
(def TELCO_FBO "data/telco-fbo.json")

(defn -main []
  (utils/try-ignore-errors
   (generate-db "hearings" HEARINGS_JSON
                :id "link")
   (generate-db "sc" SC_JSON
                :id "name")
   (generate-db "pdpc_undertakings" PDPC_UNDERTAKINGS_JSON
                :id "url"
                :ignore "raw-description")
   (generate-db "pdpc_decisions" PDPC_DECISIONS_JSON
                :id "url")
   (generate-db "lss_dt_reports" LSS_DT_REPORTS_JSON
                :id "unique_id"
                :convert "[{**item, \"unique_id\": item[\"title\"]+\"_\"+item[\"url\"]} for item in json.loads(content)]"
                :ignore "html")
   (generate-db "stc_judgments" STC_JUDGMENTS_JSON
                :id "url")
   (generate-db "fc_judgments" FC_JUDGMENTS_JSON
                :id "url")
   (generate-db "sal_specialists" SAL_SPECIALISTS_JSON
                :id "name")
   (generate-db "telco_fbo" TELCO_FBO
                :id "name")
   (utils/run-sql-file-on-db DB_FILE "scripts/create-views.sql")
   (add-computed-columns DB_FILE)
   (setup-fts "hearings"
              ["title" "parties" "offence-description"])
   (setup-fts "sc"
              ["name" "organisation"])
   (setup-fts "pdpc_decisions"
              ["title" "description" "pdf-content"])
   (setup-fts "pdpc_undertakings"
              ["organisation" "description" "pdf-content"])
   (setup-fts "lss_dt_reports"
              ["title" "content" "pdf-content"])
   (setup-fts "stc_judgments"
              ["title" "citation" "html"])
   (setup-fts "fc_judgments"
              ["title" "citation" "html"])
   (setup-fts "sal_specialists"
              ["name" "organisation"])
   (setup-fts "telco_fbo"
              ["name"])))

(def docker-compose-file "./docker/build_db.docker-compose.yml")
(defn docker []
  (shell (str "docker compose --file " docker-compose-file " build"))
  (shell (str "docker compose --file " docker-compose-file " run --rm app")))