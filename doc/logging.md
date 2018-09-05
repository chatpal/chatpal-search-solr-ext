## Solr Logging

The Chatpal request handler logs information in Json Format on every request, like:
```json
{  
   "data":{  
      "type":"query",
      "client":{  
         "collection":"chatpal_56_3KDJvz"
      },
      "query":{  
         "searchterm":"livechat",
         "querytime":25,
         "resultsize":{  
            "message":14
         }
      }
   }
}
```
Fro suggestion it looks like:
````json
{  
   "data":{  
      "type":"suggestion",
      "client":{  
         "collection":"chatpal_56_3KDJvz"
      },
      "query":{  
         "searchterm":"liv",
         "querytime":271
      }
   }
}
````

In addition it logs (scheduled) metadata of the index
```json
{  
   "data":{  
      "type":"index",
      "client":{  
         "collection":"chatpal_53_0R0Y15"
      },
      "stats":{  
         "user":{  
            "count":3
         },
         "room":{  
            "count":27
         },
         "message":{  
            "count":46
         }
      }
   }
}
```
