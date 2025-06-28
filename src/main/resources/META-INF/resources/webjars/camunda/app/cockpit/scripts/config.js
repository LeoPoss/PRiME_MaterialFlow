export default {
    customScripts: [
        //'scripts/startInstance.js',
        'scripts/sankey-plugin/sankey-plugin.js'
        // If you have a folder called 'my-custom-script' (in the 'cockpit' folder)
        // with a file called 'customScript.js' in it
        // 'my-custom-script/customScript'
    ],
    requireJsConfig: {
        //   // AngularJS module names
        //   ngDeps: ['ui.bootstrap'],
        //   // RequireJS configuration for a complete configuration documentation see:
        //   // http://requirejs.org/docs/api.html#config
        //   deps: ['jquery', 'custom-ui'],
        paths: {
            'bpmn-js': 'https://unpkg.com/bpmn-js@10.0.0/dist/bpmn-viewer.development.js',
            'd3': 'https://cdnjs.cloudflare.com/ajax/libs/d3/7.8.5/d3.min'
            //     // if you have a folder called `custom-ui` (in the `cockpit` folder)
            //     // with a file called `scripts.js` in it and defining the `custom-ui` AMD module
            //     'custom-ui': 'custom-ui/scripts'
        }
    },
    // historicActivityInstanceMetrics: {
    //   adjustablePeriod: true,
    //   //select from the default time period: day, week, month, complete
    //   period: {
    //     unit: 'week'
    //   }
    // },
    // runtimeActivityInstanceMetrics: {
    //   display: true
    // },
    'locales': {
        'availableLocales': ['${CUSTOM_TASKLIST_TITLE}', 'en'],
        'fallbackLocale': 'en'
    },
    // skipCustomListeners: {
    //   default: true,
    //   hidden: false
    // },
    // skipIoMappings: {
    //   default: true,
    //   hidden: false
    // },
    // 'batchOperation' : {
    //   // select mode of query for process instances or decision instances
    //   // possible values: filter, search
    //   'mode': 'filter',
    //
    //   // select if Historic Batches should be loaded automatically when navigating to #/batch
    //   'autoLoadEnded': true
    // },
    // bpmnJs: {
    //   moddleExtensions: {
    //     // if you have a folder called 'my-custom-moddle' (in the 'cockpit' folder)
    //     // with a file called 'camunda.json' in it defining the 'camunda' moddle extension
    //     camunda: 'my-custom-moddle/camunda'
    //   },
    //   additionalModules: [
    //     // if you have a folder called 'my-custom-module' (in the 'cockpit' folder)
    //     // with a file called 'module.js' in it
    //     'my-custom-module/module'
    //   ],
    // },
    // defaultFilter: {
    //   historicProcessDefinitionInstancesSearch: {
    //     lastDays: 5,
    //     event: 'started'
    //   }
    // },
    // csrfCookieName: 'XSRF-TOKEN',
    // disableWelcomeMessage: false,
    // userOperationLogAnnotationLength: 5000,
    // previewHtml: true
}; 