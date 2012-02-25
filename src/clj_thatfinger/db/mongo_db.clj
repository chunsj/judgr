(ns clj-thatfinger.db.mongo-db
  (:use [clj-thatfinger.db.base])
  (:require [somnium.congomongo :as mongodb])
  (:import [java.util Date]))

(defn- mongo-db-settings
  [settings]
  (-> settings :database :mongo-db))

(defn- ensure-indexes!
  "Creates all necessary MongoDB indexes."
  [conn]
  (mongodb/with-mongo conn
    (mongodb/add-index! :items [:class])
    (mongodb/add-index! :features [:feature] :unique true)))

(defn- authenticate
  "Authenticates against the specified MongoDB connection."
  [mongo-db-settings conn]
  (when (:auth? mongo-db-settings)
    (mongodb/authenticate conn
                          (:username mongo-db-settings)
                          (:password mongo-db-settings))))

(defn create-connection!
  "Creates a connection to MongoDB server."
  [settings]
  (let [settings (mongo-db-settings settings)
        conn (mongodb/make-connection (:database settings)
                                      (:host settings)
                                      (:port settings))]
    (authenticate settings conn)
    (ensure-indexes! conn)
    conn))

(deftype MongoDB [settings conn]
  ConnectionBasedDB
  (get-connection [db]
    conn)

  FeatureDB
  (add-item! [db item class]
    (ensure-valid-class settings class
      (let [data {:item item
                  :created-at (Date.)
                  :class class}]
        (mongodb/with-mongo conn
          (mongodb/insert! :items data))
        data)))

  (add-feature! [db item feature class]
    (ensure-valid-class settings class
      (mongodb/with-mongo conn
        (mongodb/update! :features {:feature feature}
                         {:$inc {:total 1
                                 (str (name :classes) "." (name class)) 1}})
        (.get-feature db feature))))

  (get-feature [db feature]
    (mongodb/with-mongo conn
      (mongodb/fetch-one :features
                         :where {:feature feature})))

  (count-features [db]
    (mongodb/with-mongo conn
      (mongodb/fetch-count :features)))

  (get-items [db]
    (mongodb/with-mongo conn
      (mongodb/fetch :items)))

  (count-items [db]
    (mongodb/with-mongo conn
      (mongodb/fetch-count :items)))

  (count-items-of-class [db class]
    (mongodb/with-mongo conn
      (mongodb/fetch-count :items
                           :where {:class class}))))