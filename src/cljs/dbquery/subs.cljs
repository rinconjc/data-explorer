(ns dbquery.subs
  (:require [ajax.core :refer [GET]]
            [clojure.string :as str]
            [re-frame.core :refer [dispatch register-sub subscribe]]
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
(register-sub
 :state
 (fn [state [_ path]]
   (if (vector? path)
     (reaction (get-in @state path))
     (reaction (get @state path)))))

(register-sub
 :db-list
 (fn [state [_]]
   (let [dbs (reaction (@state :db-list))]
     (if-not @dbs
       (GET "/data-sources" :response-format :json :keywords? true
            :handler #(dispatch [:change :db-list %])
            :error-handler #(js/console.log "failed retrieving dbs..." %)))
     dbs)))

(register-sub
 :db-tabs
 (fn [state [_]]
   (reaction (@state :db-tabs))))

(register-sub
 :db-tab/ids
 (fn [state [_]]
   (let [db-tabs (subscribe [:db-tabs])]
     (reaction (keys @db-tabs)))))

(register-sub
 :db-tab/by-id
 (fn [state [_ tab-id]]
   (let [db-tabs (subscribe [:db-tabs])]
     (reaction (get @db-tabs tab-id)))))

(register-sub
 :db-objects
 (fn [state [_ tab-id]]
   (let [db-tab (subscribe [:db-tab/by-id tab-id])
         dbobjects (reaction (get @db-tab :objects))]
     (if-not @dbobjects
       (dispatch [:load-db-objects tab-id false]))
     dbobjects)))

(register-sub
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

(register-sub
 :db-queries
 (fn [state [_ db-id]]
   (let [db-tab (subscribe [:db-tab/by-id db-id])
         queries (reaction (:db-queries @db-tab))]
     (if-not @queries
       (dispatch [:load-db-queries db-id]))
     queries)))

(register-sub
 :query
 (fn [state [_ tab-id]]
   (let [db-tab (subscribe [:db-tab/by-id tab-id])]
     (reaction (:query @db-tab)))))

(register-sub
 :resultsets
 (fn [state [_ tab-id]]
   (let [db-tab (subscribe [:db-tab/by-id tab-id])]
     (reaction (:resultsets @db-tab)))))

(register-sub
 :resultset/ids
 (fn [state [_ tab-id]]
   (let [resultsets (subscribe [:resultsets tab-id])]
     (reaction (keys @resultsets)))))

(register-sub
 :resultset/by-id
 (fn [state [_ tab-id q-id]]
   (let [resultsets (subscribe [:resultsets tab-id])]
     (reaction (get @resultsets q-id)))))

(register-sub
 :metadata
 (fn [state [_ db-id table]]
   (let [dbsubs (subscribe [:db-tab/by-id db-id])]
     (reaction (get-in @dbsubs [:meta-tables table])))))
