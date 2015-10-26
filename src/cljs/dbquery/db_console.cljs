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
         [c/button
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

(defn sql-panel [db ops active?]
  (let [cm (atom nil)
        exec-sql (fn[] (.exec-sql ops
                        (if (empty? (.getSelection @cm))
                          (.getValue @cm) (.getSelection @cm))))]
    (fn[db ops]
      (when active?
        (doto js/Mousetrap
          (. bindGlobal "ctrl+enter" exec-sql)))
      [:div.panel.panel-default.full-height {:style {:padding "0px" :margin "0px" :height "100%"}}
       [:div.panel-heading.compact
        "SQL Editor "
        [c/button-group {:bsSize "small"}
         [c/button {:title "Execute" :on-click exec-sql}
          [:i.fa.fa-play]]
         [c/button [:i.fa.fa-save]]
         [c/button [:i.fa.fa-file-o]]]]
       [:div.panel-body {:style {:padding "0px" :overflow "scroll" :height "calc(100% - 56px)"}}
        [code-mirror cm {:mode "text/x-sql"}]]
       [:div.panel-footer]])))


(deftype ConsoleControl [data-tabs active-tab q-id]
  Object
  (preview [_ tbl]
    (when-not (some #(= tbl (:id %)) @data-tabs)
      (.log js/console "adding table " tbl)
      (swap! data-tabs conj {:id tbl :raw-sql (str "select * from " tbl)}))
    (reset! active-tab tbl))

  (exec-sql [_ sql]
    (let [id (str "Query #" (swap! q-id inc))]
      (swap! data-tabs conj {:id id :raw-sql sql})
      (reset! active-tab id)))

  (delete-tab [_ t]
    (swap! data-tabs (partial remove #(= t %)))
    (if (= (:id t) @active-tab)
      (reset! active-tab (:id (first @data-tabs))))))

(defn mk-console-control [data-tabs active-tab]
  (let [q-id (atom 0)]
    (ConsoleControl. data-tabs active-tab q-id)))

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
                           [:span id
                            [c/close-button #(.delete-tab ops t)]])}
            [query-table db t]])]]])))
