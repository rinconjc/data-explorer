(ns dbquery.subs
  (:require [ajax.core :refer [GET]]
            [clojure.string :as str]
            [re-frame.core :refer [dispatch reg-sub-raw subscribe]]
            [reagent.ratom :refer-macros [reaction]]))

;; model
;; {:db-tabs {1 {:db {} :objects [] :query {:id nil :sql "" :name "" }
;;             :result-sets {"q1" {:data {:rows [] :columns []}
;;                            :query (Query.) :loading false
;;                            :count nil :col-data {"colname" []}}}
;;             :active-table "q1
;;             :db-out
;;             :q :selected}}
;;  :active-db 1 :db-details {} :db-list [] :user {} :modal []}

;; register subscriptions
(reg-sub-raw
 :state
 (fn [state [_ path]]
   (if (vector? path)
     (reaction (get-in @state path))
     (reaction (get @state path)))))

(reg-sub-raw
 :db-list
 (fn [state [_]]
   (let [dbs (reaction (@state :db-list))]
     (if-not @dbs
       (GET "/data-sources" :response-format :json :keywords? true
            :handler #(dispatch [:change :db-list %])
            :error-handler #(js/console.log "failed retrieving dbs..." %)))
     dbs)))

(reg-sub-raw
 :db-tabs
 (fn [state [_]]
   (reaction (@state :db-tabs))))

(reg-sub-raw
 :db-tab/ids
 (fn [state [_]]
   (let [db-tabs (subscribe [:db-tabs])]
     (reaction (keys @db-tabs)))))

(reg-sub-raw
 :db-tab/by-id
 (fn [state [_ tab-id]]
   (let [db-tabs (subscribe [:db-tabs])]
     (reaction (get @db-tabs tab-id)))))

(reg-sub-raw
 :db-objects
 (fn [state [_ tab-id]]
   (let [db-tab (subscribe [:db-tab/by-id tab-id])
         dbobjects (reaction (get @db-tab :objects))]
     (if-not @dbobjects
       (dispatch [:load-db-objects tab-id false]))
     dbobjects)))

(reg-sub-raw
 :db-objects-model
 (fn [state [_ tab-id]]
   (let [db-tab (subscribe [:db-tab/by-id tab-id])
         db-objects (subscribe [:db-objects tab-id])
         model (reaction (select-keys @db-tab [:q :selected]))]
     (reaction (assoc @model
                      :items (if (str/blank? (:q @model))
                               @db-objects
                               (filter #(re-find (-> @model :q str/upper-case re-pattern)
                                                 (% :name)) @db-objects)))))))

(reg-sub-raw
 :db-queries
 (fn [state [_ db-id]]
   (let [db-tab (subscribe [:db-tab/by-id db-id])
         queries (reaction (:db-queries @db-tab))]
     (if-not @queries
       (dispatch [:load-db-queries db-id]))
     queries)))

(reg-sub-raw
 :query
 (fn [state [_ tab-id]]
   (let [db-tab (subscribe [:db-tab/by-id tab-id])]
     (reaction (:query @db-tab)))))

(reg-sub-raw
 :resultsets
 (fn [state [_ tab-id]]
   (let [db-tab (subscribe [:db-tab/by-id tab-id])]
     (reaction (:resultsets @db-tab)))))

(reg-sub-raw
 :resultset/ids
 (fn [state [_ tab-id]]
   (let [resultsets (subscribe [:resultsets tab-id])]
     (reaction (->> @resultsets vals (sort-by :pos) (map :id))))))

(reg-sub-raw
 :resultset/by-id
 (fn [state [_ tab-id q-id]]
   (let [resultsets (subscribe [:resultsets tab-id])]
     (reaction (get @resultsets q-id)))))

(reg-sub-raw
 :metadata
 (fn [state [_ db-id table]]
   (let [dbsubs (subscribe [:db-tab/by-id db-id])]
     (reaction (get-in @dbsubs [:meta-tables table])))))

(reg-sub-raw
 :col-meta
 (fn [state [_ db-id table]]
   (let [metadata (subscribe [:metadata db-id table])]
     (if-not @metadata
       (dispatch [:load-meta-table db-id table]))
     (reaction
      (into {} (for [{:strs [name type_name fk_column fk_table]} @metadata
                     :let [col (keyword name)]]
                 (if (some? fk_table)
                   [name {:type :link :fk_table fk_table :fk_column fk_column
                          :db-id db-id}]
                   [name {:type type_name}])))))))

(reg-sub-raw
 :active-record
 (fn [state [_ db-id]]
   (let [db-state (subscribe [:db-tab/by-id db-id])]
     (reaction (:active-record @db-state)))))
