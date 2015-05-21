(ns rook.swagger-spec
  (:use io.aviso.rook
        speclj.core
        clojure.repl
        clojure.template
        clojure.pprint)
  (:require [io.aviso.rook.dispatcher :as d]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [io.aviso.rook.swagger :as sw]
            [io.aviso.rook :as rook]
            [clj-http.client :as client]
            [io.aviso.toolchest.collections :refer [pretty-print]]
            [qbits.jet.server :as jet]
            [io.aviso.tracker :as t]
            [clojure.tools.logging :as l]
            [io.aviso.rook.server :as server]
            [io.aviso.rook.schema :as rs]
            [schema.core :as s])
  (:import [org.eclipse.jetty.server Server]
           [javax.servlet.http HttpServletResponse]))

(defn- swagger-object
  [rook-options swagger-options & ns-specs]
  (let [[_ routing-table] (d/construct-namespace-handler rook-options ns-specs)]
    (sw/construct-swagger-object swagger-options routing-table)))

(defn get*
  [path]
  (client/get (str "http://localhost:8192" path)
              {:throw-exceptions false
               :accept           :json}))

(defmacro should-have-status
  [expected-status response]
  `(let [response# ~response
         expected-status# ~expected-status]
     (if (= expected-status# (:status response#))
       response#
       (-fail (format "Expected status %d in response:\n%s"
                      expected-status#
                      (pretty-print response#))))))


(def swagger-options (-> sw/default-swagger-options
                         (assoc-in [:template :info :title] "Hotels and Rooms API")
                         (assoc-in [:template :info :version] "Pre-Alpha")))

(comment
  (binding [t/*log-trace-level* :info]
    (-> (swagger-object nil swagger-options
                        ["hotels" 'hotels
                         [[:hotel-id "rooms"] 'rooms]])
        (json/generate-string {:pretty true})
        println)))

(defn start-server
  []
  (let [creator #(rook/namespace-handler {:swagger-options swagger-options}
                                         ["hotels" 'hotels
                                          [[:hotel-id "rooms"] 'rooms]])
        handler (server/construct-handler {:log true :track true :standard true :exceptions true} creator)
        ^Server server (jet/run-jetty {:ring-handler handler
                                       :join?        false
                                       :port         8192})]
    #(.stop server)))

(describe "io.aviso.rook.swagger"

  (it "can construct swagger data"
      (should-not-be-nil (swagger-object nil swagger-options
                                         ["hotels" 'hotels
                                          [[:hotel-id "rooms"]
                                           'rooms]])))

  (context "responses"

    (with-all response-description
              (rs/with-description "Error Body Description"
                                   {:error                    (rs/with-description "logical error name" s/Str)
                                    (s/optional-key :message) (rs/with-description "user presentable message" s/Str)}))

    (it "can capture schema descriptions"
        (should= {:path {:responses {200 {:description "Error Body Description"
                                          :schema      {:required   [:error]
                                                        :properties {:error   {:type        :string
                                                                               :description "logical error name"}
                                                                     :message {:type        :string
                                                                               :description "user presentable message"}}}}}}}

                 (sw/default-responses-injector sw/default-swagger-options {} {:meta {:responses {200 @response-description}}} [:path]))))

  (context "end-to-end (synchronous)"

    (with-all handler (-> (rook/namespace-handler {:swagger-options swagger-options}
                                                  ["hotels" 'hotels
                                                   [[:hotel-id "rooms"] 'rooms]])
                          rook/wrap-with-standard-middleware))

    (with-all server (jet/run-jetty {:ring-handler @handler
                                     :join?        false
                                     :port         8192}))

    (it "can start the server"
        (should-not-be-nil @server))

    (after-all
      (.stop @server))

    (it "can expose the resource listing"

        ;; The result is so large and complex, there isn't much we can do to check it here.
        (->> (get* "/swagger.json")
             (should-have-status HttpServletResponse/SC_OK)))))

(run-specs)
