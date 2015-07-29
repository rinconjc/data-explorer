angular.module('data.import', ['ui.bootstrap'])
    .run(function($rootScope, $modal, fileUpload){
        $rootScope.$on('data.import', function(e){
            console.debug('data.import');
            $modal.open({
                templateUrl:'tpls/data-import.html',
                controller:function($scope, $modalInstance){
                    $scope.model = {};
                    $scope.cancel = function(){
                        $modalInstance.dismiss();                        
                    };                    
                    $scope.upload = function(){
                        console.debug('Uploading ...', $scope.model);
                        fileUpload.uploadFile($scope.model.file, 'upload');
                    };
                    $scope.form = [
                        {field:'file', type:'file'},
                        {field:'separator', type:'select', label:'Separator',values:{',':'Comma', '\t':'TAB'}},
                        {type:'button', value:'Upload', handler:$scope.upload}
                    ]                    
                }
            })
        });
    })
;
