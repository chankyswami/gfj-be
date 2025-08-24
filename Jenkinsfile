pipeline {
    agent {
        docker {
            image 'maven-terraform-agent:latest'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    environment {
        EC2_INSTANCE_IP     = '13.203.132.105'
        EC2_INSTANCE_USER   = 'ec2-user'
        DEPLOY_PATH         = '/home/ec2-user'
        AWS_REGION          = 'ap-south-1'
        TF_PLAN_FILE        = 'tfplan'
    }

    parameters {
        booleanParam(name: 'APPLY_TF', defaultValue: false, description: 'Apply Terraform changes?')
        booleanParam(name: 'DESTROY_TF', defaultValue: false, description: 'Destroy infrastructure instead of running pipeline?')
    }

    stages {
        stage('Terraform Destroy') {
            when {
                expression { return params.DESTROY_TF == true }
            }
            steps {
                echo "💣 Destroying infrastructure..."
                withAWS(credentials: 'GEMS-AWS', region: "${env.AWS_REGION}") {
                    sh '''
                        cd terraform-gem/environments/dev
                        terraform init -input=false
                        terraform destroy -auto-approve
                    '''
                }
            }
        }

        stage('Terraform Init & Plan') {
            when {
                expression { return params.DESTROY_TF == false }
            }
            steps {
                echo "🌍 Initializing and planning Terraform..."
                withAWS(credentials: 'GEMS-AWS', region: "${env.AWS_REGION}") {
                    sh '''
                        cd terraform-gem/environments/dev
                        terraform init -input=false
                        terraform plan -out=${TF_PLAN_FILE}
                    '''
                }
            }
        }

        stage('Terraform Apply') {
            when {
                expression { return params.APPLY_TF == true && params.DESTROY_TF == false }
            }
            steps {
                echo "🚀 Applying Terraform changes..."
                withAWS(credentials: 'GEMS-AWS', region: "${env.AWS_REGION}") {
                    sh '''
                        cd terraform-gem/environments/dev
                        terraform apply -auto-approve ${TF_PLAN_FILE}
                    '''
                }
            }
        }

        stage('Build') {
            when {
                expression { return params.DESTROY_TF == false }
            }
            steps {
                sh 'echo "🔧 Checking Maven version..."'
                sh 'mvn -v'

                sh 'echo "🛠️ Building the project..."'
                sh 'mvn clean package -DskipTests=true || exit 1'

                sh 'echo "📦 Listing JARs in target directory..."'
                sh 'ls -lh target/*.jar || echo "❌ No JAR found!"'

                script {
                    def jarFile = sh(script: "ls target/*.jar | grep SNAPSHOT | head -n 1", returnStdout: true).trim()
                    env.JAR_NAME = jarFile.tokenize('/').last()
                    echo "📌 Detected JAR: ${env.JAR_NAME}"
                }
            }
        }

        stage('Deploy') {
            when {
                expression { return params.DESTROY_TF == false }
            }
            steps {
                echo "🚚 Deploying to EC2 instance..."
                configFileProvider([configFile(fileId: 'start-gfj-script', variable: 'START_SCRIPT_PATH')]) {
                    sshagent(credentials: ['ec2-creds']) {
                        sh """
                            echo "📤 Copying JAR to EC2..."
                            scp -o StrictHostKeyChecking=no target/${JAR_NAME} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/

                            echo "✏️ Preparing start-gfj.sh with correct JAR name..."
                            sed "s|gfj-be-0.0.30-SNAPSHOT.jar|${JAR_NAME}|g" ${START_SCRIPT_PATH} > start-gfj-updated.sh

                            echo "📤 Copying updated start script to EC2..."
                            scp -o StrictHostKeyChecking=no start-gfj-updated.sh ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/start-gfj.sh

                            echo "➕ Giving execute permissions to start-gfj.sh..."
                            ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} '
                                sudo chmod +x ${DEPLOY_PATH}/start-gfj.sh
                            '

                            echo "🔄 Restarting application on EC2..."
                            ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} '
                                echo "🛑 Stopping existing app..." &&
                                sudo pkill -f "${JAR_NAME}" || true &&
                                echo "▶️ Starting new app..." &&
                                bash ${DEPLOY_PATH}/start-gfj.sh
                            '
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Job finished successfully!'
        }
        failure {
            echo '❌ Job failed. Check the logs for more details.'
        }
    }
}
