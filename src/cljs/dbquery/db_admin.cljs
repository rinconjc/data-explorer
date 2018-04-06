(ns dbquery.db-admin
  (:require [dbquery.commons :as c :refer [button dispatch-all input]]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r :refer [atom]]
            [reagent.ratom :refer-macros [reaction]]))

(defn database-window [db-initial]
  (let [model (subscribe [:state :edit-db])
        db-spec (atom db-initial)]
    (fn [db-initial]
      [c/modal {:on-hide #(dispatch [:change :modal nil :edit-db nil])
                :show (not (:saved @model))}
       [c/modal-header
        [:h4 "Database Connection"]]
       [c/modal-body
        [:div.container-fluid
         (if (:error @model)
           [c/alert {:bs-style "danger"} (:error @model)])
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
           ^{:key 1}[:option {:value "ORACLE"} "ORACLE"]
           ^{:key 2}[:option {:value "H2"} "H2"]
           ^{:key 3}[:option {:value "POSTGRES"} "PostgreSQL"]
           ^{:key 4}[:option {:value "Sybase"} "Sybase"]
           ^{:key 5}[:option {:value "MS-SQL"} "MS SQL"]]
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
                 :on-click #(dispatch [:save-db @db-spec])}
         "Connect"]]])))

(defn select-db-dialog [dbs]
  (let [selected (atom 0)]
    (fn [dbs]
      [c/modal {:on-hide #(dispatch [:change :modal nil]) :bsSize "small" :show true}
       [c/modal-header
        [:h4 "Open Database"]]
       [c/modal-body
        [:div.container-fluid
         [:form.form-horizontal {:on-submit #(dispatch [:open-db (dbs @selected)])}
          [input {:value @selected :type "select" :label "Database" :auto-focus true
                  :on-change #(reset! selected (-> % .-target .-value))}
           (map-indexed (fn [i db] ^{:key i}
                          [:option {:value i} (db :name)]) dbs)]]]]
       [c/modal-footer
        [button {:bsStyle "primary"
                 :on-click #(dispatch-all [:open-db (dbs @selected)]
                                          [:change :modal nil])} "Connect"]
        [button {:bsStyle "primary"
                 :on-click #(dispatch [:edit-db (:id (dbs @selected))])} "Configure"]]])))
