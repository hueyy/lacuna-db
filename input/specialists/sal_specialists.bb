(ns input.specialists.sal-specialists
  (:require [babashka.pods :as pods]
            [babashka.http-client :as http]
            [input.utils.general :as utils]
            [cheshire.core :as json]
            [clojure.string :as str]))

(pods/load-pod 'retrogradeorbit/bootleg "0.1.9")

(require '[pod.retrogradeorbit.hickory.select :as s])

(def URL "https://sal.org.sg/wp-admin/admin-ajax.php")
(def JSON_FILE "data/sal-specialists.json")
(def NONCE "c40bb7d341")

(defn fetch-list []
  (-> #(http/post URL {:headers {"User-Agent" utils/USER_AGENT}
                       :form-params {:action "ymc_get_posts"
                                     :nonce_code NONCE
                                     :params (json/encode {:cpt "gs_team"
                                                           :tax "gs_team_group,gs_team_tag"
                                                           :terms "149,148,147,213,214"
                                                           :default_terms ""
                                                           :exclude_posts "on"
                                                           :choices_posts ""
                                                           :posts_selected "all"
                                                           :preloader_icon "preloader_3"
                                                           :type_pg "numeric"
                                                           :per_page "100000"
                                                           :page "1"
                                                           :page_scroll "1"
                                                           :preloader_filters "none"
                                                           :preloader_filters_rate "0.5"
                                                           :preloader_filters_custom ""
                                                           :post_animation ""
                                                           :popup_animation "zoom-in"
                                                           :letter ""
                                                           :post_layout "post-layout1"
                                                           :filter_layout "filter-layout5"
                                                           :filter_id "18399"
                                                           :search ""
                                                           :search_filtered_posts "0"
                                                           :carousel_params ""
                                                           :filter_date ""
                                                           :sort_order ""
                                                           :sort_orderby ""
                                                           :meta_key ""
                                                           :meta_query ""
                                                           :date_query ""
                                                           :data_target "data-target-ymc1"
                                                           :target_id "1"})}})
      (utils/retry-func 10 60)
      :body
      (json/parse-string true)
      :data
      (utils/parse-html)))

(defn extract-practice-area [article]
  (let [category-spans (->> article
                            (s/select (s/descendant
                                       (s/class "category")
                                       (s/tag :span))))]
    (->> category-spans
         (filter #(not (re-find #"accredited-specialist|senior-accredited-specialist" (get-in % [:attrs :class] ""))))
         (first)
         (utils/get-el-content)
         (utils/clean-string))))

(defn extract-designation [article]
  (let [category-spans (->> article
                            (s/select (s/descendant
                                       (s/class "category")
                                       (s/tag :span))))]
    (->> category-spans
         (filter #(re-find #"accredited-specialist|senior-accredited-specialist" (get-in % [:attrs :class] "")))
         (first)
         (utils/get-el-content)
         (utils/clean-string))))

(defn extract-name [article]
  (->> article
       (s/select (s/descendant (s/class "title") (s/tag :a)))
       (first)
       (utils/get-el-content)
       (utils/clean-string)))

(defn extract-image-url [article]
  (->> article
       (s/select (s/descendant (s/tag :img)))
       (first)
       :attrs
       :src))

(defn parse-article [article]
  (let [practice-area (extract-practice-area article)
        designation (extract-designation article)
        name (extract-name article)
        image-url (extract-image-url article)
        post-id (->> article
                     (s/select (s/descendant (s/class "title") (s/tag :a)))
                     (first)
                     :attrs
                     :data-postid)]
    {:name name
     :practice-area practice-area
     :designation designation
     :image-url image-url
     :post-id post-id}))

(defn fetch-detail [post-id]
  (-> #(http/post URL {:headers {"User-Agent" utils/USER_AGENT}
                       :form-params {:action "get_post_popup"
                                     :nonce_code NONCE
                                     :post_id post-id
                                     :filter_id "18399"
                                     :target_id "1"}})
      (utils/retry-func 10 60)
      :body
      (json/parse-string true)
      :data
      (utils/parse-html)))

(defn extract-organisation [detail-html]
  (->> detail-html
       (s/select (s/descendant
                  (s/class "popup-content")
                  (s/tag :a)))
       (first)
       (utils/get-el-content)
       (utils/clean-string)))

(defn extract-year-of-accreditation [detail-html]
  (let [validity-text (->> detail-html
                           (s/select (s/descendant
                                      (s/class "popup-content")
                                      (s/tag :em)))
                           (first)
                           (utils/get-el-content)
                           (utils/clean-string))]
    (str/replace validity-text #"^\s*Validity:\s+" "")))

(defn extract-url [detail-html]
  (->> detail-html
       (s/select (s/descendant
                  (s/class "popup-content")
                  (s/tag :a)))
       (first)
       :attrs
       :href))

(defn enrich-specialist-data [specialist]
  (let [detail-html (-> specialist :post-id (fetch-detail))
        organisation (extract-organisation detail-html)
        year-of-accreditation (extract-year-of-accreditation detail-html)
        url (extract-url detail-html)]
    (assoc specialist
           :organisation organisation
           :year-of-accreditation year-of-accreditation
           :url url)))

(defn get-accredited-specialists []
  (->> (fetch-list)
       (s/select (s/class "ymc-post-layout1"))
       (pmap parse-article)
       (map enrich-specialist-data)))


(defn -main []
  (->> (get-accredited-specialists)
       (json/generate-string)
       (spit JSON_FILE)))
