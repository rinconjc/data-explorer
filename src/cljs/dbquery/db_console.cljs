(ns dbquery.db-console
  (:require [dbquery.commons :as c :refer [input]]
            [dbquery.data-table :as dt]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r :refer [atom]]
            [widgets.splitter :as st]
            [reagent.ratom :refer-macros [reaction]]
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
        selected (atom nil)
        icons {"TABLE" "fa-table fa-fw"
               "VIEW" "fa-copy fa-fw"}]
    (fn [tab-id]
      [:div.full-height.panel.panel-default
       [:div.panel-heading.compact
        [c/button-group {:bsSize "small"}
         [c/button {:on-click #(dispatch [:load-db-objects true])}
          [:i.fa.fa-refresh {:title "Refresh Objects"}]]
         [c/button {:on-click #(if @selected
                                 (dispatch [:preview-table (-> @model :items (nth @selected) :name)]))}
          [:i.fa.fa-list-alt {:title "Preview Data"}]]
         [c/button {:on-click #(if @selected
                                 (dispatch [:table-meta (-> @model :items (nth @selected) :name)]))}
          [:i.fa.fa-info {:title "Show metadata"}]]]]
       [:div.panel-body {:style {:padding "4px 4px"}}
        (if (:q @model)
          [search-box #(dispatch [:filter-objects %])])
        [:ul {:class "list-unstyled list" :style {:height "100%" :cursor "pointer"}
              :on-key-down #(some-> % .-key {"ArrowDown" inc "ArrowUp" dec
                                             "Enter" (fn[s] (dispatch [:preview-table (-> @model :items (nth s) :name)]))}
                                    (apply @selected nil)
                                    (max 0) (min (dec (count (:items @model))))
                                    ((partial reset! selected)))}
         (doall
          (map-indexed
           (fn [i {:keys[type name]}] ^{:key name}
             [:li {:class (if (= i @selected) "selected" "")
                   :tabIndex 101
                   :on-click #(reset! selected i)
                   :on-double-click #(dispatch [:preview-table name])}
              [:i.fa {:class (icons type)}] name]) (:items @model)))]]])))

(defn code-mirror [instance config value]
  (r/create-class
   {:reagent-render
    (fn[instance config value]
      (if @instance
        (.setValue @instance value))
      [:textarea.mousetrap {:rows 10 :style {:width "100%" :height "100%"}} " "])
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

(defn share-query [db-list q-id]
  (let [ids (atom (into #{} (for [db db-list :when (:query_id db)] (:id db))))]
    (fn [db-list q-id]
      [:div.my-popover {:style {:margin-top "37px" :padding "5px"}}
       [:form
        (doall
         (map-indexed
          (fn[i db] ^{:key i}
            [:div.checkbox
             [:label
              [:input {:type "checkbox" :value (:id db) :checked (contains? @ids (:id db))
                       :on-change #(swap! ids (if (-> % .-target .-checked) conj disj) (:id db))}]
              (:name db)]]) db-list))
        [c/button {:bsStyle "primary"
                   :on-click #(dispatch [:assign-query q-id @ids])} "Save"]]])))

(defn sql-panel [id]
  (let [cm (atom nil)
        query (subscribe [:query id])
        suggestions (subscribe [:db-queries id])
        query-assocs (subscribe [:state :query-assocs])
        model (atom {})
        save-fn #(if (nil? @query)
                   (dispatch [:change :modal [query-form id {:sql (.getValue @cm)}]])
                   (dispatch [:save-query (assoc @query :sql (.getValue @cm))]))
        query-filter (fn[text]
                       (let [re (re-pattern text)]
                         (filter #(re-find re (or (:name %) "")) @suggestions)))
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
          [c/button {:title "Save" :on-click save-fn}
           [:i.fa.fa-save]]
          [c/button {:on-click reset-fn}
           [:i.fa.fa-file-o]]]
         [c/button-group {:bsSize "small"}
          [:form.form-inline {:on-submit #(.preventDefault %)}
           [c/input {:model [model :search] :type "typeahead"
                     :placeholder "search queries" :size 20 :tab-index 1
                     :data-source query-filter :result-fn #(:name %)
                     :choice-fn #(dispatch [:set-in-active-db :query %]) }]
           [c/button {:on-click #(dispatch [:load-db-queries])} [:i.fa.fa-refresh]]]]
         [c/button-group
          [c/button {:title "share" :disabled (nil? (:id @query))
                     :on-click #(dispatch [:query-sharings (:id @query)])} [:i.fa.fa-share]]
          (if @query-assocs
            [share-query @query-assocs (:id @query)])]
         [c/button-group
          [:span (:name @query)]]]]
       [:div.panel-body {:style {:padding "0px" :overflow "scroll" :height "calc(100% - 46px)"}}
        [code-mirror cm {:mode "text/x-sql"
                         :tabindex 2 :autofocus true
                         :extraKeys {:Ctrl-Enter exec-sql :Alt-S save-fn}}
         (or (:sql @query) "  ")]]])))

(defn metadata-table [db-id {:keys [table] :as model}]
  (let [data (subscribe [:metadata db-id table])]
    (fn [db-id {:keys [subs-key] :as model}]
      [dt/data-table (assoc model :data
                            {:rows @data
                             :columns ["name" "type_name" "data_type" "size" "digits" "nullable"
                                       "is_pk" "is_fk" "fk_table" "fk_column"]}
                            :last-page? true
                            :loading false)])))

(defn record-view [db-id]
  (let [data (subscribe [:active-record db-id])]
    (fn [db-id]
      [:div.my-popover {:style {:padding "10px"}}
       (if @data
         [:div
          [:h4.center-block.bg-default (-> @data :key :fk_table)]
          [:dl.dl-horizontal
           (for [[k v] (:data @data)]
             ^{:key k} [:span [:dt k] [:dd (or v "(null)")]])]]
         [:h4 "loading..."])])))

(defn link-cell [metadata v]
  (let [show? (atom false)]
    (fn [metadata v]
      [:div {:on-mouse-leave #(reset! show? false)}
       [:a.btn-link
        {:on-click
         #(if (reset! show? (not @show?))
            (dispatch [:load-record
                       (assoc (select-keys metadata [:fk_table :fk_column]) :value v)]))}
        v]
       (if @show? [record-view (:db-id metadata)])])))

(defmethod dt/table-cell :link [metadata v] [link-cell metadata v])

(defn preview-table [db-id rs]
  (let [metadata (subscribe [:col-meta db-id (:table rs)])]
    (fn [db-id rs]
      [dt/data-table rs (map #(get @metadata %) (-> rs :data :columns))])))

(defn db-console [id]
  (let [db-tab (subscribe [:db-tab/by-id id])]
    (fn[id]
      [st/horizontal-splitter {:split-at 240}
       [db-objects id]
       [st/vertical-splitter {:split-at 200}
        [sql-panel id]
        [c/tabs {:active-key (:active-table @db-tab)
                 :on-select #(dispatch [:activate-table id %])
                 :class "small-tabs full-height"}
         (if-let [exec-rows (:execution @db-tab)]
           [c/tab {:event-key :exec-log
                   :title (r/as-element
                           [:span "SQL Execution"
                            [c/close-button #(dispatch [:set-in-active-db id :execution nil])]])}
            [:div {:style {:height "100%" :overflow-y "scroll"}}
             [:ul.list-group
              (for [x exec-rows] ^{:key (:id x)}
                [:li.list-group-item
                 [:span.pull-right.small
                  (cond (= :executing (:status x)) [:i.fa.fa-spinner.fa-spin]
                        (:error x) [:span.red (:error x)]
                        (:update-count x) (str (:update-count x) " rows " (:time x) "s")
                        :else (str (:time x) "s"))] (:sql x)])]]])
         (for [[rs-id rs] (:resultsets @db-tab)]
           ^{:key rs-id}
           [c/tab {:event-key rs-id
                   :title (r/as-element
                           [:span {:title rs-id} rs-id
                            [c/close-button #(dispatch [:kill-table id rs-id])]])}
            (case (:type rs)
              :metadata [metadata-table id rs]
              :preview [preview-table id rs]
              [dt/data-table rs])])]]])))
