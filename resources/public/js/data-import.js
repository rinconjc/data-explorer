angular.module('data-import', ['ui.bootstrap', 'common-utils', 'common-widgets'])
    .run(function($rootScope, $modal, CommonUtils, DataService){
        $rootScope.$on('data.import', function(evt){
            $modal.open({
                templateUrl:'tpls/data-import.html',
                size:'lg',
                controller:function($scope, $modalInstance){
                    $scope.model = {separator:'\t', hasHeader:true};
                    $scope.cancel = function(){
                        $modalInstance.dismiss();
                    };
                    $scope.upload = function(){
                        console.debug('Uploading ...', $scope.model);
                        $scope.loaded = CommonUtils.postFormData('upload', $scope.model);
                    };
                    $scope.form = [
                        {field:'file',label:'File', type:'file'},
                        {field:'separator',label:'Separator', type:'select', options:{'\t':'Tab',',':'Comma'}},
                        {field:'hasHeader',label:'Header', type:'checkbox'},
                        {type:'button', value:'Upload', handler:$scope.upload, style:'primary'}
                    ];
                    $scope.dest={};
                    function updateTables(){
                        $scope.destParams[1].options = DataService.getTables($scope.dest.database);
                    }
                    $scope.destParams = [
                        {field:'database', label:'Database', type:'select', onchange:updateTables,
                         options:CommonUtils.toObject(DataService.getDatasources(), 'id', 'name')},
                        {field:'table', label:'Table', type:'select', options:{}}
                    ]
                }
            })
        });
    })
;
