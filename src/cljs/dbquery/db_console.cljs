(ns dbquery.db-console
  (:require [dbquery.commons :as c]
            [widgets.splitter :as st]
            [dbquery.data-table :refer [query-table]]
            [reagent.core :as r :refer [atom]]
            [clojure.string :as s]
            [cljsjs.codemirror]
            [cljsjs.mousetrap]
            [ajax.core :refer [GET POST]]))


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
                        :placeholder "search..." :size 35 :style {:width "100%"}}])}))

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
        (js/Mousetrap.bind "alt+d" #((:preview-table ops) (@selected "name")))
        (js/Mousetrap.bind "/" #(swap! search? not))
        (js/Mousetrap.bind "esc" #(reset! search? false)))
      [:div.full-height.panel.panel-default
       [:div.panel-heading.compact
        [c/button-group
         [c/button [:span.glyphicon.glyphicon-refresh
                    {:title "Refresh Objects"
                     :on-click #(retrieve-db-objects db tables error :refresh true)}]]
         [c/button {:on-click #((:preview-table ops) (@selected "name"))}
          [:span.glyphicon.glyphicon-list-alt {:title "Preview Data"}]]
         [c/button
          [:span.glyphicon.glyphicon-info-sign {:title "Show metadata"}]]]]
       [:div.panel-body {:style {:padding "4px 4px"}}
        (if @search?
          [search-box search-fn])
        [:span @error]
        [:ul {:class "list-unstyled list" :style {:height "100%" :cursor "pointer"}}
         (doall (for [tb (or (and @search? @filtered) @tables)]
                  ^{:key (tb "name")}
                  [:li {:class (if (= tb @selected) "selected" "")
                        :on-click #(reset! selected tb)}
                   [:i.fa {:class (icons (tb "type"))}] (tb "name")]))]]])))

(defn code-mirror [config]
  (r/create-class
   {:reagent-render (fn[config] [:textarea {:style {:width "100%" :height "100%"}}  "--SQL code here"])
    :component-did-mount #(.fromTextArea js/CodeMirror (r/dom-node %) (clj->js config))
    }))

(defn sql-panel [db]
  [:div.panel.panel-default.full-height {:style {:padding "0px" :margin "0px" :height "100%"}}
   [:div.panel-heading.compact
    "SQL Editor"]
   [:div.panel-body {:style {:padding "0px" :overflow "hidden" :height "calc(100% - 48px)"}}
    [code-mirror {:mode "text/x-sql" :autofocus true}]]
   [:div.panel-footer]])

(defn db-console [db active?]
  (let [active-tab (atom nil)
        data-tabs (atom [])
        ops {:preview-table
             (fn[tbl]
               (when-not (some #(= tbl (:id %)) @data-tabs)
                 (.log js/console "adding table " tbl)
                 (swap! data-tabs conj {:id tbl :raw-sql (str "select * from " tbl)}))
               (reset! active-tab tbl))}]
    (fn[db active?]
      [st/horizontal-splitter {:split-at 240}
       [db-objects db ops active?]
       [st/vertical-splitter {:split-at 200}
        [sql-panel db]
        [c/tabs {:activeKey @active-tab :on-select #(reset! active-tab %)
                 :class "small-tabs full-height"}
         (for [t @data-tabs]
           ^{:key (:id t)}
           [c/tab {:eventKey (:id t)
                   :title (r/as-element [:span (:id t)
                                         [c/close-button (fn[_](swap! data-tabs (partial remove #(= t %))))]])}
            [query-table db t]])]]])))
