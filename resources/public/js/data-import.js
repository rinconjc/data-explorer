angular.module('data-import', ['ui.bootstrap', 'common-utils', 'common-widgets'])
    .run(function($rootScope, $modal, CommonUtils, DataService){
        $rootScope.$on('data.import', function(evt){
            $modal.open({
                templateUrl:'tpls/data-import.html',
                size:'lg',
                controller:function($scope, $modalInstance){
                    var FORMAT_REQUIRED_TYPES = [91,92,93,3,8,2,7];
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
                        var tables = DataService.getTables($scope.dest.database);
                        tables.push('New Table');
                        $scope.destParams[1].options = CommonUtils.toObject(tables);
                    }
                    
                    function tableChanged(){
                        if($scope.dest.table=='_'){
                            $scope.destParams[2].hide=false;
                        }else{
                            $scope.tableMeta = DataService.getTableInfo($scope.dest.database, $scope.dest.table);
                        }   
                    }
                    $scope.requiresFormat = function(col){
                        return FORMAT_REQUIRED_TYPES.indexOf(col.data_type)>=0;
                    };
                    $scope.destParams = [
                        {field:'database', label:'Database', type:'select', onchange:updateTables,
                         options:CommonUtils.toObject(DataService.getDatasources(), 'id', 'name')},
                        {field:'table', label:'Table', type:'select', onchange:tableChanged, options:{}, staticOptions:{'_':'< New Table >'}},
                        {field:'newTable', type:'input', hide:true, placeholder:'Table Name'}
                    ];
                    $scope.doImport = function(){
                        console.log('importing into...', $scope.dest);
                                                
                    };
                }
            })
        });
    })
;
