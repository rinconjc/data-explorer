(ns dbquery.handlers
  (:require [ajax.core
             :refer
             [default-interceptors DELETE GET POST PUT success? to-interceptor]]
            [ajax.protocols :refer [-body -status]]
            [clojure.string :as str]
            [dbquery.commons :refer [error-text dispatch-all]]
            [dbquery.sql-utils
             :refer
             [next-order
              query-from-sql
              query-from-table
              set-order
              sql-distinct
              sql-select
              sql-statements]]
            [re-frame.core :as rf :refer [debug dispatch register-handler trim-v]]
            [secretary.core :as secretary :include-macros true]
            [clojure.set :as set]))

(defn log-ex
  [handler]
  (fn log-ex-handler
    [db v]
    (try
      (handler db v)        ;; call the handler with a wrapping try
      (catch :default e     ;; ooops
        (do
          (.error js/console e.stack)   ;; print a sane stacktrace
          db)))))

(def common-middlewares [trim-v (if ^boolean goog.DEBUG log-ex)
                         ;;(when ^boolean goog.DEBUG debug)
                         ])

;; add ajax interceptor
(defn empty-means-nil [response]
  (if-not (str/blank? (-body response))
    response
    (reduced [(-> response -status success?) nil])))

(def treat-nil-as-empty
  (to-interceptor {:name "JSON special case nil"
                   :response empty-means-nil}))

(swap! default-interceptors concat [treat-nil-as-empty])

(defn error-handler [res]
  (dispatch [:change :status [:error (error-text res)]]))

(defn tab-path
  [handler]
  (fn tab-handler
    [state [tab-id :as v]]
    (update-in state [:db-tabs tab-id] handler v)))

(defn in-active-db
  [handler]
  (fn [state v]
    (if-let [active-tab (:active-tab state)]
      (update-in state [:db-tabs active-tab] handler (cons active-tab v))
      state)))

(defn in-active-table
  [handler]
  (in-active-db
   (fn [state v]
     (if-let [active-table (:active-table state)]
       (update-in state [:resultsets active-table] handler v)
       state))))

(defn with-selected-table
  [handler]
  (fn [state v]
    (js/console.log "state:" (-> state :selected ))
    (if-let [selected-table (-> state :selected :name)]
      (handler state (cons selected-table v))
      state)))

;; register handlers
(register-handler
 :init-db
 common-middlewares
 (fn [state []]
   (or state {:db-tabs (array-map)})))

(register-handler
 :save-db
 [common-middlewares]
 (fn [state [db-details]]
   (let [params [:params db-details :format :json
                 :response-format :json
                 :handler #(dispatch [:db-saved db-details])
                 :error-handler #(dispatch [:change [:edit-db :error] (error-text %)])]]
     (if (:id db-details)
       (apply PUT (str "/data-sources/" (:id db-details)) params)
       (apply POST "/data-sources" params )))
   state))

(register-handler
 :load-db-objects
 [common-middlewares in-active-db]
 (fn [state [tab-id reload?]]
   (GET (str "/ds/" tab-id "/tables?refresh=" reload?)
        :keywords? true :format :json :response-format :json
        :handler #(dispatch [:change [:db-tabs tab-id :objects] %])
        :error-handler #(dispatch [:change [:db-tabs tab-id :error] (error-text %)]))
   state))

(register-handler
 :filter-objects
 [common-middlewares in-active-db]
 (fn [state [tab-id q]]
   (assoc state :q q)))

(register-handler
 :activate-db
 common-middlewares
 (fn [state [tab-id]]
   (let [current (:active-tab state)]
     (if-not (= current tab-id)
       (assoc state :active-tab tab-id)
       state))))

(register-handler
 :open-db
 common-middlewares
 (fn [state [db]]
   (or (when-let [id (:id db)]
         (dispatch [:activate-db id])
         (if (get-in state [:db-tabs id]) state
             (update state :db-tabs assoc id {:db db :name (:name db) :resultsets (array-map)})))
       state)))

(register-handler
 :kill-db
 common-middlewares
 (fn [state [tab-id]]
   (let [new-state (update state :db-tabs dissoc tab-id)]
     (update new-state :active-tab #(if (= % tab-id) (first (keys (:db-tabs state))) %)))))

(register-handler
 :activate-table
 [common-middlewares debug tab-path]
 (fn [state [db-id q-id]]
   (let [current (:active-table state)
         now (.getTime (js/Date.))
         last-change (or (:last-change state) 0)]
     (if (or (= current q-id) (< (- now last-change) 1000)) state
         (assoc state :active-table q-id :last-change now)))))

(register-handler
 :kill-table
 [common-middlewares tab-path]
 (fn [state [db-id q-id]]
   (let [new-state (update state :resultsets dissoc q-id)]
     (if (= q-id (:active-table state))
       (assoc new-state :active-table (-> new-state :resultsets keys first))
       new-state))))

;; event handlers
(register-handler
 :login
 (fn [state [_ login-data]]
   (POST "/login" :format :json
         :params login-data
         :handler #(dispatch [:login-ok %])
         :error-handler #(dispatch [:server-error %]))
   state))

(register-handler
 :login-ok
 (fn [state [_ user-info]]
   (secretary/dispatch! "/")
   (assoc state :user user-info)))

(register-handler
 :server-error
 (fn [state [_ resp]]
   (assoc state :error (error-text resp))))

(register-handler
 :register-user
 (fn [state [_ user-data]]
   (POST "/users" :format :json :params user-data
         :handler #(dispatch [:user-register-ok %])
         :error-handler #(dispatch [:server-error %]))
   state))

(register-handler
 :user-register-ok
 (fn [state [_ _]]
   (secretary/dispatch! "/login")
   state))

(register-handler
 :logout
 (fn [state _]
   (secretary/dispatch! "/logout")
   (dissoc state :user)))

(register-handler
 :change
 common-middlewares
 (fn [state [key value & kvs]]
   (loop [[k v] [key value] kvs kvs s state]
     (cond
       (nil? k) s
       (vector? k) (recur (take 2 kvs) (drop 2 kvs) (assoc-in s k v))
       :else (recur (take 2 kvs) (drop 2 kvs) (assoc s k v))))))

(register-handler
 :change-page
 (fn [state [_ page auth-required?]]
   (if (or (not auth-required?) (:user state))
     (assoc state :current-page page)
     (do
       (GET "/user" :handler #(dispatch [:login-ok %])
            :error-handler #(secretary/dispatch! "/login"))
       state))))

(register-handler
 :update
 [common-middlewares tab-path]
 (fn [state [tab-id keys f & args]]
   (update-in state (if (vector? keys) keys [keys]) #(apply f % args))))

(register-handler
 :set-in-active-db
 [debug common-middlewares in-active-db]
 (fn [state [_ key val]]
   (assoc state key val)))

(register-handler
 :load-db-queries
 [common-middlewares in-active-db]
 (fn [state [db-id]]
   (GET (str "/ds/" db-id "/queries")
        :response-format :json :keywords? true :format :json
        :handler #(dispatch [:change [:db-tabs db-id :db-queries] %])
        :error-handler #(js/console.log %))
   state))

(register-handler
 :assign-query
 [common-middlewares]
 (fn [state [q-id ds-ids]]
   (let [old (reduce #(if (:query_id %2) (conj %1 (:id %2)) %1) #{} (:query-assocs state))
         added-ids (set/difference ds-ids old)
         removed-ids (set/difference old ds-ids)]
     (when (seq added-ids)
       (POST (str "/queries/" q-id "/data-sources")
             :params {:ds-ids added-ids}
             :format :json
             :handler #(dispatch [:change :query-assocs nil])
             :error-handler #(js/console.log "failed assigning query " %)))
     (when (seq removed-ids)
       (DELETE (str "/queries/" q-id "/data-sources")
               :params {:ds-ids removed-ids}
               :format :json
               :handler #(dispatch [:change :query-assocs nil])
               :error-handler #(js/console.log "failed assigning query " %))))
   state))

(register-handler
 :save-query
 [trim-v in-active-db]
 (fn [state [tab-id query]]
   (let [[method path] (if-let [id (:id query)]
                         [PUT (str "/queries/" id)] [POST "/queries"])]
     (method path :params query :format :json :response-format :json :keywords? true
             :handler #(do (dispatch [:change [:db-tabs tab-id :query] % :modal nil])
                           (if-not (:id query) (dispatch [:assign-query (:id %) #{tab-id}])))
             :error-handler #(do
                               (js/console.log (str "failure saving:" (error-text %)))
                               (dispatch [:change :modal nil])))
     state)))

(register-handler
 :submit-sql
 [common-middlewares tab-path]
 (fn [state [db-id sql]]
   (dispatch [:update db-id :in-queue
              concat (for [q (sql-statements sql)] q)])
   (dispatch [:exec-queries db-id])
   state))

(register-handler
 :exec-queries
 [common-middlewares debug tab-path]
 (fn [state [db-id]]
   (update state :in-queue
           (fn [[q & more]]
             (if (some? q)
               (dispatch [:exec-query db-id q]))
             more))))

(register-handler
 :exec-query
 [common-middlewares tab-path]
 (fn [state [db-id q offset limit]]
   (if (map? q)
     (dispatch [:update db-id [:resultsets (:id q)] assoc :loading true]))
   (let [offset (or offset 0)
         limit (or limit 40)
         qid (inc (state :exec-count 0))
         sql (if (string? q) q (sql-select (:query q)))]
     (POST (str "/ds/" db-id "/exec-sql")
           :params {:sql sql :offset offset :limit limit}
           :response-format :json :format :json :keywords? true
           :handler #(do
                       (dispatch [:exec-done db-id qid q offset % nil])
                       (dispatch [:exec-queries db-id]))
           :error-handler #(dispatch [:exec-done db-id qid q offset nil (error-text %)]))
     (-> state (update :execution conj {:sql sql :id qid :status :executing
                                        :start (. js/Date now)})
         (assoc :exec-count qid)))))

(defmulti update-result (fn [q & _]
                          (js/console.log "multi of " q)
                          (cond (string? q) :string :else :default) ))

(defmethod update-result :string [q state data offset]
  (let [qnum (inc (or (state :result-count) 0))
        q {:id (str "Result #" qnum) :data data
           :query (query-from-sql q)}]
    (-> state (update :resultsets assoc (:id q) q)
        (assoc :result-count qnum)
        ((if (seq (:in-queue state)) identity
             #(assoc % :active-table (:id q)))))))

(defmethod update-result :default [q state data offset]
  (-> state (update-in [:resultsets (:id q)] merge q
                       {:data (if (pos? (or offset 0))
                                (update (:data q) :rows concat (:rows data)) data)
                        :loading false})
      (assoc :active-table (:id q))))

(register-handler
 :exec-done
 [common-middlewares tab-path]
 (fn [state [db-id qid q offset resp error]]
   (cond-> state
     true (update :execution
             (fn[xs](map #(if (= (:id %) qid)
                            (assoc % :status :done :error error
                                   :time (/ (- (. js/Date now) (:start %)) 1000.0)
                                   :update-count (and (not error) (:rowsAffected resp)))
                            %) xs)))
     error (assoc :in-queue nil :active-table :exec-log)
     (map? q) (update-in [:resultsets (:id q)] assoc :loading false)
     (:data resp) (#(update-result q % (:data resp) offset)))))

(register-handler
 :preview-table
 [common-middlewares in-active-db]
 (fn [state [tab-id table]]
   (if (get-in state [:resultsets table])
     (assoc state :active-table table)
     (do (dispatch [:exec-query tab-id {:id table :type :preview
                                        :query (query-from-table table)
                                        :table table}])
         state))))

(register-handler
 :table-meta
 [debug common-middlewares in-active-db]
 (fn [state [tab-id table reload?]]
   (let [id (str table "*")
         rs (get-in state [:resultsets id])]
     (if (or reload? (not rs))
       (dispatch [:load-meta-table tab-id table reload?]))
     (-> state (assoc :active-table id)
         (update :resultsets assoc id
                 (if-not rs {:id id :type :metadata :table table :loading true}
                         (assoc rs :loading true)))))))

(register-handler
 :load-meta-table
 [debug common-middlewares]
 (fn [state [db-id table reload?]]
   (GET (str "/ds/" db-id "/tables/" table) :response-format :json
        :params {:refresh reload?}
        :handler #(dispatch [:update db-id [:meta-tables] assoc table (% "columns")])
        :error-handler #(.log js/console %))
   state))

(register-handler
 :next-page
 [common-middlewares in-active-table]
 (fn [resultset [tab-id]]
   (if-not (or (:loading resultset) (:last-page? resultset))
     (dispatch [:exec-query tab-id resultset (-> resultset :data :rows count)]))
   resultset))

(register-handler
 :set-sort
 [common-middlewares in-active-table]
 (fn [resultset [tab-id col-index ord]]
   (let [new-rs (update-in resultset [:query :order] set-order col-index (fn [_] ord))]
     (dispatch [:exec-query tab-id new-rs 0 (-> resultset :data :rows count)])
     new-rs)))

(register-handler
 :roll-sort
 [common-middlewares in-active-table]
 (fn [resultset [tab-id col-index]]
   (let [new-rs (update-in resultset [:query :order] set-order col-index next-order)]
     (dispatch [:exec-query tab-id new-rs 0 (-> resultset :data :rows count)])
     new-rs)))

(register-handler
 :set-filter
 [common-middlewares in-active-table]
 (fn [resultset [tab-id col condition]]
   (let [new-rs (update-in resultset [:query :conditions]
                           #(if (str/blank? condition)
                              (dissoc % col)  (assoc % col condition)))]
     (dispatch [:exec-query tab-id new-rs])
     new-rs)))

(register-handler
 :query-dist-values
 [common-middlewares in-active-table]
 (fn [resultset [tab-id col]]
   (if-not (get-in resultset [:col-data col])
     (POST (str "/ds/" tab-id "/exec-sql")
           :params {:sql (sql-distinct (:query resultset) col) :limit 10}
           :response-format :json :format :json :keywords? true
           :handler #(dispatch [:update tab-id [:resultsets (:id resultset) :col-data col]
                                (fn[_] (-> % :data :rows flatten))])
           :error-handler #(.log js/console %)))
   resultset))

(register-handler
 :reload
 [debug common-middlewares in-active-table]
 (fn [resultset [tab-id]]
   (case (:type resultset)
     :metadata (dispatch [:table-meta (:table resultset) true])
     (dispatch [:exec-query tab-id resultset 0 (-> resultset :data :rows count)]))
   resultset))

(register-handler
 :query-sharings
 [common-middlewares]
 (fn [state [q-id]]
   (GET (str "/queries/" q-id "/data-source") :format :json :response-format :json
        :keywords? true
        :handler #(dispatch [:change :query-assocs %])
        :error-handler #(.log js/console %))
   state))

(register-handler
 :load-record
 [common-middlewares in-active-db]
 (fn load-record [state [db-id {:keys [fk_table fk_column value] :as key}]]
   (let [active-row (:active-record state)]
     (when-not (and active-row (= key (:key active-row)))
       (POST (str "/ds/" db-id "/exec-sql")
             :params {:sql (str "select * from " fk_table " where "
                                fk_column "=" (if (string? value) (str \' value \') value))}
             :format :json :keywords? true :response-format :json
             :handler #(dispatch [:update db-id :active-record assoc
                                  :key key :data (mapv list (-> % :data :columns)
                                                       (-> % :data :rows first))])
             :error-handler #(js/console.log "error:" %))
       (dissoc state :active-record))
     state)))

(register-handler
 :show-tab
 [debug common-middlewares]
 (fn [state [tab-id tab-name]]
   (dispatch [:activate-db tab-id])
   (update state :db-tabs assoc tab-id {:id tab-id :name tab-name})))
