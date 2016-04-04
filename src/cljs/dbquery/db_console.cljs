(ns dbquery.db-console
  (:require [dbquery.commons :as c :refer [open-modal input button]]
            [widgets.splitter :as st]
            [dbquery.data-table :as dt]
            [reagent.core :as r :refer [atom]]
            [clojure.string :as s]
            [cljsjs.codemirror]
            [cljsjs.mousetrap]
            [ajax.core :refer [GET POST PUT]]
            [clojure.string :as str]
            [re-frame.core :as rf]))

(deftype ConsoleControl [data-tabs active-tab q-id out-text]
  Object
  (preview [_ tbl]
    (when-not (some #(= tbl (:id %)) @data-tabs)
      (.log js/console "adding table " tbl)
      (swap! data-tabs conj {:id tbl :query (dt/query-from-table tbl)}))
    (reset! active-tab tbl))

  (exec-sql [_ sql data]
    (let [id (str "Query #" (swap! q-id inc))]
      (swap! data-tabs conj {:id id :query (dt/query-from-sql sql) :data data})
      (reset! active-tab id)))

  (delete-tab [_ t]
    (swap! data-tabs (partial remove #(= t %)))
    (if (= (:id t) @active-tab)
      (reset! active-tab (:id (first @data-tabs)))))

  (info [_ tbl]
    (let [id (str tbl "*")]
      (when-not (some #(= id (:id %)) @data-tabs)
        (swap! data-tabs conj {:id id :table tbl}))
      (reset! active-tab id)))

  (output [_ text]
    (let [id "output"]
      (reset! out-text text)
      (when-not (some #(= id (:id %)) @data-tabs)
        (swap! data-tabs #(vec (cons {:id id :text @out-text} %))))
      (reset! active-tab id))))

(defn mk-console-control [data-tabs active-tab]
  (let [q-id (atom 0)
        out-text (atom nil)]
    (ConsoleControl. data-tabs active-tab q-id out-text)))

(defn retrieve-db-objects [db resp-atom error-atom & {:keys [refresh]}]
  (GET (str "/ds/" (db "id") "/tables?refresh=" refresh) :response-format :json
       :handler #(reset! resp-atom %)
       :error-handler #(reset! error-atom %)))

(defn search-box [f]
  (r/create-class
   {:component-did-mount #(.focus (r/dom-node %))
    :reagent-render (fn[f]
                      [:input.form-control.mousetrap
                       {:on-change #(f (-> % .-target .-value))
                        :placeholder "search..." :size 35 :style {:width "100%"}
                        :tabIndex 100}])}))

(defn db-objects [db ops active?]
  (let [tables (atom [])
        filtered (atom nil)
        error (atom nil)
        selected (atom nil)
        search? (atom false)
        icons {"TABLE" "fa-table fa-fw"
               "VIEW" "fa-copy fa-fw"}
        search-fn (fn[text]
                    (let [re (re-pattern (s/upper-case text))]
                      (reset! filtered (filter #(re-find re (% "name")) @tables))))]
    (retrieve-db-objects db tables error)
    (fn [db ops active?]
      (when active?
        (doto js/Mousetrap
          (.bind "alt+d" #(.preview ops (@selected "name")))
          (.bind "/" #(swap! search? not))
          (.bind "esc" #(reset! search? false))))
      [:div.full-height.panel.panel-default
       [:div.panel-heading.compact
        [c/button-group {:bsSize "small"}
         [c/button {:on-click #(retrieve-db-objects db tables error :refresh true)}
          [:i.fa.fa-refresh {:title "Refresh Objects"}]]
         [c/button {:on-click #(.preview ops (@selected "name"))}
          [:i.fa.fa-list-alt {:title "Preview Data"}]]
         [c/button {:on-click #(.info ops (@selected "name"))}
          [:i.fa.fa-info {:title "Show metadata"}]]]]
       [:div.panel-body {:style {:padding "4px 4px"}}
        (if @search?
          [search-box search-fn])
        [:span @error]
        [:ul {:class "list-unstyled list" :style {:height "100%" :cursor "pointer"}}
         (doall (for [{:strs[type name] :as tb} (or (and @search? @filtered) @tables)]
                  ^{:key name}
                  [:li {:class (if (= tb @selected) "selected" "") :tabIndex 101
                        :on-click #(reset! selected tb)
                        :on-double-click #(.preview ops name)}
                   [:i.fa {:class (icons type)}] name]))]]])))

(defn code-mirror [instance config]
  (r/create-class
   {:reagent-render (fn[config] [:textarea.mousetrap {:style {:width "100%" :height "100%"}}])
    :component-did-mount (fn[c]
                           (let [cm (.fromTextArea js/CodeMirror (r/dom-node c) (clj->js config))]
                             (.setTimeout js/window #(.focus cm) 1000)
                             (reset! instance cm)))}))

(defn query-form [tab-id query]
  (let [show? (atom true)]
    (fn [tab-id query]
      [c/modal {:on-hide #(reset! show? false) :show @show? :bsSize "small"}
       [c/modal-header [:h4 "Save Query"]]
       [c/modal-body
        [:div.container-fluid
         [:form.form-horizontal
          [input {:model [query :name] :type "text" :label "Name" :placeholder "Query name"}]
          [input {:model [query :description] :type "textarea" :label "Description"}]]]
        [c/modal-footer
         [c/button {:bsStyle "primary"
                    :on-click #(rf/dispatch [:save-query tab-id @query
                                             (fn [] (reset! show? false))]) }
          "Save"]
         [c/button {:bsStyle "default" :on-click #(reset! show? false)}
          "Close"]]]])))

(defn sql-panel [db ops active?]
  (let [cm (atom nil)
        tab-id (db "id")
        state (rf/subscribe [:state [:tabs tab-id :sql-panel]])
        model (atom {})
        save-fn #(if (:query @state) (rf/dispatch [:save-query tab-id (.getValue @cm)])
                     (open-modal [query-form tab-id (atom {:sql (.getValue @cm)})]))
        exec-sql
        (fn[]
          (let [sql (if (empty? (.getSelection @cm))
                      (.getValue @cm) (.getSelection @cm))]
            (dt/execute-query db {:raw-sql sql}
                              #(if-let [data (% "data")]
                                 (.exec-sql ops sql data)
                                 (.output ops (str "rows affected :" (% "rowsAffected"))))
                              #(.output ops (c/error-text %)))))]
    (fn[db ops active?]
      (when active?
        (doto js/Mousetrap
          (.bindGlobal "ctrl+enter" exec-sql)))
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
          [c/button [:i.fa.fa-file-o]]]
         [c/button-group {:bsSize "small"}
          [:form.form-inline
           [c/input {:model [model :search] :type "text" :placeholder "search queries" :size 40}]]]]]
       [:div.panel-body {:style {:padding "0px" :overflow "scroll" :height "calc(100% - 46px)"}}
        [code-mirror cm {:mode "text/x-sql"}
         (get-in @state [:query :sql])]]])))

(defn retrieve-table-meta [db tbl data-fn error-fn]
  (GET (str "/ds/" (db "id") "/tables/" tbl) :response-format :json
       :handler data-fn :error-handler error-fn))

(defn table-meta [db tbl]
  (let [data (atom {})
        controller
        (reify Object
          (refresh [this]
            (retrieve-table-meta db tbl
                                 #(reset! data {"rows" (% "columns")
                                                "columns" ["name" "type_name" "size" "digits" "nullable" "is_pk" "is_fk" "fk_table" "fk_column"]}) #(.log js/console %)))
          (sort [_ _]
            (.log js/console "sort not implemented"))
          (next-page [this]
            (.log js/console "next-page not implemented")))]
    (.refresh controller)
    (fn[db tbl]
      [dt/data-table data controller])))

(defn sql-output [v]
  [:div (:text v)])

(defn db-console [db active?]
  (let [active-tab (atom nil)
        data-tabs (atom [])
        ops (mk-console-control data-tabs active-tab)]
    (fn[db active?]
      [st/horizontal-splitter {:split-at 240}
       [db-objects db ops active?]
       [st/vertical-splitter {:split-at 200}
        [sql-panel db ops active?]
        [c/tabs {:activeKey @active-tab
                 :on-select #(reset! active-tab %)
                 :class "small-tabs full-height"}
         (for [t @data-tabs :let [id (:id t)]]
           ^{:key id}
           [c/tab {:eventKey id
                   :title (r/as-element
                           [:span {:title (:title t)} id
                            [c/close-button #(.delete-tab ops t)]])}
            (cond
              (:query t) [dt/query-table db (:query t) (:data t)]
              (:table t) [table-meta db (:table t)]
              :else [sql-output t])])]]])))
