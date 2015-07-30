angular.module('data.import', ['ui.bootstrap'])
    .run(function($rootScope, $modal, fileUpload){
        $rootScope.$on('data.import', function(e){
            console.debug('data.import');
            $modal.open({
                templateUrl:'tpls/data-import.html',
                size:'lg',
                controller:function($scope, $modalInstance){
                    $scope.model = {};
                    $scope.cancel = function(){
                        $modalInstance.dismiss();                        
                    };                    
                    $scope.upload = function(){
                        console.debug('Uploading ...', $scope.model);
                        fileUpload.uploadFile($scope.model.file, 'upload', $scope.model);
                    };
                    $scope.form = [
                        {field:'file',label:'File', type:'file'},
                        {field:'separator',label:'Column Separator', type:'select', values:{'\\t':'Tab',',':'Comma'}},
                        {type:'button', value:'Upload', handler:$scope.upload}
                    ]                    
                }
            })
        });
    })
;
