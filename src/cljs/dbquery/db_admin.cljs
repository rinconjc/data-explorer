(ns dbquery.db-admin
  (:require [dbquery.commons :as c :refer [input button error-text]]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST PUT]]))

(defn database-window [db-id when-done]
  (let[show? (atom true)
       error (atom nil)
       db-spec (atom {})]
    (if db-id
      (GET (str "/data-sources/" db-id) :response-format :json :keywords? true :format :json
           :handler #(reset! db-spec %) :error-handler #(.log js/console (c/error-text %))))

    (fn [db-id when-done]
      [c/modal {:on-hide #(reset! show? false) :show @show?}
       [c/modal-header
        [:h4 "Database Connection"]]
       [c/modal-body
        [:div.container-fluid
         (if @error
           [c/alert {:bs-style "danger"} @error])
         [:form {:class-name "form-horizontal"}
          [input {:model [db-spec :name] :type "text"
                  :label "Connection Name"
                  :placeholder "Unique name"
                  :label-class-name "col-sm-4"
                  :wrapper-class-name "col-sm-6"}]
          [input {:model [db-spec :dbms] :type "select"
                  :label "Database Type"
                  :placeholder "Select database type"
                  :label-class-name "col-sm-4"
                  :wrapper-class-name "col-sm-6"}
           [:option {:key "" :disabled ""} ""]
           [:option {:key "ORACLE"} "ORACLE"]
           [:option {:key "H2"} "H2"]
           [:option {:key "POSTGRES"} "PostgreSQL"]]
          [input {:model [db-spec :url]
                  :type "text" :label "URL"
                  :placeholder "<server>:<port>..."
                  :label-class-name "col-sm-4"
                  :wrapper-class-name "col-sm-8"}]
          [input {:model [db-spec :schema]
                  :type "text" :label "Schema"
                  :label-class-name "col-sm-4"
                  :wrapper-class-name "col-sm-5"}]
          [input {:model [db-spec :user_name]
                  :type "text" :label "User"
                  :label-class-name "col-sm-4"
                  :wrapper-class-name "col-sm-5"}]
          [input {:model [db-spec :password]
                  :type "password" :label "Password"
                  :label-class-name "col-sm-4"
                  :wrapper-class-name "col-sm-5"}]]]]
       [c/modal-footer
        [button {:bsStyle "primary"
                 :on-click
                 (fn [e]
                   (let [params [:params @db-spec :format :json
                                 :response-format :json
                                 :handler #(do (when-done)
                                               (reset! show? false))
                                 :error-handler #(reset! error (error-text %))]]
                     (if (:id @db-spec)
                       (apply PUT (str "/data-sources/" (:id @db-spec)) params)
                       (apply POST "/data-sources" params ))))}
         "Connect"]]])))

(defn select-db-dialog [return-fn]
  (let [show? (atom true)
        db-id (atom nil)
        dbs (atom [])
        handle-ok (fn [action]
                    (reset! show? false)
                    (return-fn action (@dbs @db-id)))]
    (GET "/data-sources" :response-format :json
         :handler #(reset! dbs %)
         :error-handler #(js/console.log "failed retrieving dbs..." %))
    (fn [return-fn]
      [c/modal {:show @show? :on-hide #(reset! show? false) :bsSize "small"}
       [c/modal-header
        [:h4 "Open Database"]]
       [c/modal-body
        [:div.container-fluid
         [:form.form-horizontal {:on-submit handle-ok}
          [input {:value @db-id :type "select" :label "Database"
                  :on-change #(reset! db-id (-> % .-target .-value))}
           [:option {:key "" :disabled true}  "Select a Database"]
           (for [[i db] (map-indexed vector @dbs)]
             ^{:key i}[:option {:value i} (db "name")])]]]]
       [c/modal-footer
        [button {:bsStyle "primary"
                 :on-click #(handle-ok :connect)} "Connect"]
        [button {:bsStyle "primary"
                 :on-click #(handle-ok :configure)} "Configure"]]])))
