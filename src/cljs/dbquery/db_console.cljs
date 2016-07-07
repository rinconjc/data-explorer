(ns dbquery.db-console
  (:require [dbquery.commons :as c :refer [input open-modal]]
            [dbquery.data-table :as dt]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r :refer [atom]]
            [widgets.splitter :as st]
            [cljsjs.codemirror]))

(defn search-box [f]
  (r/create-class
   {:component-did-mount #(.focus (r/dom-node %))
    :reagent-render (fn[f]
                      [:input.form-control.mousetrap
                       {:on-change #(f (-> % .-target .-value))
                        :placeholder "search..." :size 35 :style {:width "100%"}
                        :tabIndex 100}])}))

(defn db-objects [tab-id]
  (let [model (subscribe [:db-objects-model tab-id])
        icons {"TABLE" "fa-table fa-fw"
               "VIEW" "fa-copy fa-fw"}]
    (fn [tab-id]
      [:div.full-height.panel.panel-default
       [:div.panel-heading.compact
        [c/button-group {:bsSize "small"}
         [c/button {:on-click #(dispatch [:load-db-objects true])}
          [:i.fa.fa-refresh {:title "Refresh Objects"}]]
         [c/button {:on-click #(dispatch [:preview-table])}
          [:i.fa.fa-list-alt {:title "Preview Data"}]]
         [c/button {:on-click #(dispatch [:table-meta])}
          [:i.fa.fa-info {:title "Show metadata"}]]]]
       [:div.panel-body {:style {:padding "4px 4px"}}
        (if (:q @model)
          [search-box #(dispatch [:filter-objects %])])
        [:ul {:class "list-unstyled list" :style {:height "100%" :cursor "pointer"}}
         (doall (for [{:keys[type name] :as tb} (:items @model)]
                  ^{:key name}
                  [:li {:class (if (= tb (:selected @model)) "selected" "") :tabIndex 101
                        :on-click #(dispatch [:set-in-active-db :selected tb])
                        :on-double-click #(dispatch [:preview-table])}
                   [:i.fa {:class (icons type)}] name]))]]])))

(defn code-mirror [instance config]
  (r/create-class
   {:reagent-render
    (fn[config] [:textarea.mousetrap {:style {:width "100%" :height "100%"}}])
    :component-did-mount
    (fn[c]
      (let [cm (.fromTextArea js/CodeMirror (r/dom-node c) (clj->js config))]
        (.setTimeout js/window #(.focus cm) 1000)
        (reset! instance cm)))}))

(defn query-form [tab-id initial-query]
  (let [query (atom initial-query)]
    (fn [tab-id initial-query]
     [c/modal {:on-hide #(dispatch [:change :modal nil]) :show true :bsSize "small"}
      [c/modal-header [:h4 "Save Query"]]
      [c/modal-body
       [:div.container-fluid
        [:form.form-horizontal
         [input {:model [query :name] :type "text" :label "Name" :placeholder "Query name"}]
         [input {:model [query :description] :type "textarea" :label "Description"}]]]
       [c/modal-footer
        [c/button {:bsStyle "primary"
                   :on-click #(dispatch [:save-query @query])}
         "Save"]
        [c/button {:bsStyle "default" :on-click #(dispatch [:change :modal nil])}
         "Close"]]]])))

(defn sql-panel [id]
  (let [cm (atom nil)
        query (subscribe [:query id])
        suggestions (subscribe [:db-queries id])
        model (atom {})
        save-fn #(if (nil? @query)
                   (dispatch [:change :modal [query-form id {:sql (.getValue @cm)}]])
                   (dispatch [:save-query (assoc @query :sql (.getValue @cm))]))
        query-filter (fn[text]
                       (let [re (re-pattern text)]
                         (filter #(re-find re (% "name")) @suggestions)))
        exec-sql #(dispatch [:submit-sql id (if (empty? (.getSelection @cm))
                                              (.getValue @cm) (.getSelection @cm))])
        reset-fn #(do
                    (dispatch [:set-in-active-db :query nil])
                    (.setValue @cm ""))]

    (fn[id]
      [:div.panel.panel-default.full-height {:style {:padding "0px" :margin "0px" :height "100%"}}
       [:div.panel-heading.compact
        [c/button-toolbar
         [c/button-group {:style {:margin-top "5px"}} "SQL Editor"]
         [c/button-group
          [c/button {:title "Execute" :on-click exec-sql}
           [:i.fa.fa-play]]
          [c/button {:title "Save"
                     :on-click save-fn}
           [:i.fa.fa-save]]
          [c/button {:on-click reset-fn}
           [:i.fa.fa-file-o]]]
         [c/button-group {:bsSize "small"}
          [:form.form-inline {:on-submit #(identity false)}
           [c/input {:model [model :search] :type "typeahead" :placeholder "search queries" :size 40
                     :data-source query-filter :result-fn #(% :name)
                     :choice-fn #(.setValue @cm (% "sql")) }]]]
         [:span (:name @query)]]]
       [:div.panel-body {:style {:padding "0px" :overflow "scroll" :height "calc(100% - 46px)"}}
        [code-mirror cm {:mode "text/x-sql"
                         :extraKeys {:Ctrl-Enter exec-sql :Alt-S save-fn}}
         (or (get @query :sql) "--...")]]])))

(defn db-console [id]
  (let [db-tab (subscribe [:db-tab/by-id id])]
    (fn[id]
      [st/horizontal-splitter {:split-at 240}
       [db-objects id]
       [st/vertical-splitter {:split-at 200}
        [sql-panel id]
        [c/tabs {:activeKey (:active-table @db-tab)
                 :on-select #(dispatch [:activate-table id %])
                 :class "small-tabs full-height"}
         (if-let [out (:db-out @db-tab)]
           [c/tab {:eventKey :out :title "SQL Output"}
            [:div out]])
         (doall (for [[rs-id rs] (:resultsets @db-tab)]
                  ^{:key rs-id}
                  [c/tab {:eventKey rs-id
                          :title (r/as-element
                                  [:span {:title rs-id} rs-id
                                   [c/close-button #(dispatch [:kill-table id rs-id])]])}
                   [dt/data-table id rs-id]]))]]])))
