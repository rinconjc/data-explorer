(ns dbquery.db-console
  (:require [dbquery.commons :as c]
            [widgets.splitter :as st]
            [dbquery.data-table :refer [query-table]]
            [reagent.core :as r :refer [atom]]
            [cljsjs.codemirror]
            [cljsjs.mousetrap]
            [ajax.core :refer [GET POST]]))


(defn retrieve-db-objects [db resp-atom error-atom & {:keys [refresh]}]
  (GET (str "/ds/" (db "id") "/tables?refresh=" refresh) :response-format :json
       :handler #(reset! resp-atom %)
       :error-handler #(reset! error-atom %)))

(defn db-objects [db ops active?]
  (let [tables (atom [])
        error (atom nil)
        selected (atom nil)
        search? (atom false)
        icons {"TABLE" "fa-table fa-fw"
               "VIEW" "fa-copy fa-fw"}
        _ (retrieve-db-objects db tables error)]

    (fn [db ops active?]
      (when active?
        (js/Mousetrap.bind "alt+d" #((:preview-table ops) (@selected "name")))
        (js/Mousetrap.bind "/" #(swap! search? not))
        (js/Mousetrap.bind "esc" #(reset! search? false)))
      [:div {:class "full-height panel panel-default"}
       [:div.panel-heading {:class "compact"}
        [c/button-group
         [c/button [:span.glyphicon
                    {:class "glyphicon-refresh" :title "Refresh Objects"
                     :on-click #(retrieve-db-objects db tables error :refresh true)}]]
         [c/button {:on-click #((:preview-table ops) (@selected "name"))}
          [:span.glyphicon.glyphicon-list-alt {:title "Preview Data"}]]
         [c/button
          [:span.glyphicon.glyphicon-info-sign {:title "Show metadata"}]]]]
       [:div.panel-body {:style {:padding "4px 4px"}}
        (if @search? (with-meta [:input.form-control {:placeholder "search..." :size 35 :style {:width "100%"}}]
                       {:component-did-mount #(.focus (r/dom-node %))}))
        [:span @error]
        [:ul {:class "list-unstyled list" :style {:height "100%" :cursor "pointer"}}
         (doall (for [tb @tables]
                  ^{:key (tb "name")}
                  [:li {:class (if (= tb @selected) "selected" "")
                        :on-click #(reset! selected tb)}
                   [:i.fa {:class (icons (tb "type"))}] (tb "name")]))]]])))

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
        [:div "active?:" (if active? "active" "not-active")]
        [c/tabs {:activeKey @active-tab :on-select #(reset! active-tab %)
                 :class "small-tabs full-height"}
         (for [t @data-tabs]
           ^{:key (:id t)}
           [c/tab {:eventKey (:id t)
                   :title (r/as-element [:span (:id t)
                                         [c/close-button (fn[_](swap! data-tabs (partial remove #(= t %))))]])}
            [query-table db t]])]]])))
