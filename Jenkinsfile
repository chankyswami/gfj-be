pipeline {
    agent {
        docker {
            image 'maven-terraform-agent:latest'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    environment {
        EC2_INSTANCE_IP     = '13.202.224.13'
        EC2_INSTANCE_USER   = 'ec2-user'
        DEPLOY_PATH         = '/home/ec2-user'
        AWS_REGION          = 'ap-south-1'
        TF_PLAN_FILE        = 'tfplan'
    }

    parameters {
        booleanParam(name: 'APPLY_TF', defaultValue: false, description: 'Apply Terraform changes?')
    }

    stages {
        stage('Terraform Init & Plan') {
            steps {
                echo "🌍 Initializing and planning Terraform..."
                withAWS(credentials: 'GEMS-AWS', region: "${env.AWS_REGION}") {
                    sh '''
                        echo "📁 Contents of current directory:"
                        ls -la
                        # Navigate to the directory containing Terraform files
                        cd terraform-gem
                        echo "📁 Contents of terraform-gem directory:"
                        ls -la

                        # Expose AWS credentials as environment variables for Terraform
                        export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
                        export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
                        export AWS_SESSION_TOKEN="${AWS_SESSION_TOKEN}"

                        terraform init -input=false
                        terraform plan -out=${TF_PLAN_FILE}
                    '''
                }
            }
        }

        stage('Terraform Apply') {
            when {
                expression { return params.APPLY_TF == true }
            }
            steps {
                echo "🚀 Applying Terraform changes..."
                withAWS(credentials: 'GEMS-AWS', region: "${env.AWS_REGION}") {
                    sh '''
                        # Navigate to the directory containing Terraform files
                        cd terraform-gem
                        
                        # Expose AWS credentials as environment variables for Terraform
                        export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
                        export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
                        export AWS_SESSION_TOKEN="${AWS_SESSION_TOKEN}"

                        terraform apply -auto-approve ${TF_PLAN_FILE}
                    '''
                }
            }
        }

        stage('Build') {
            steps {
                sh 'echo "🔧 Checking Maven version..."'
                sh 'mvn -v'

                sh 'echo "🛠️ Building the project..."'
                sh 'mvn clean install -DskipTests=true || exit 1'

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
            steps {
                echo "🚚 Deploying to EC2 instance..."
                configFileProvider([configFile(fileId: 'start-gfj-script', variable: 'START_SCRIPT_PATH')]) {
                    sshagent(credentials: ['ec2-creds']) {
                        sh """
                            echo "📤 Copying JAR and start script to EC2..."
                            scp -o StrictHostKeyChecking=no target/${JAR_NAME} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/
                            scp -o StrictHostKeyChecking=no ${START_SCRIPT_PATH} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/start-gfj.sh

                            echo "🔄 Restarting application on EC2..."
                            ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} '
                                echo "🛑 Stopping existing app..." &&
                                sudo pkill -f "${JAR_NAME}" || true &&
                                sudo chmod +x ${DEPLOY_PATH}/start-gfj.sh &&
                                echo "▶️ Starting new app..." &&
                                sudo nohup bash ${DEPLOY_PATH}/start-gfj.sh > /var/log/gfj.log 2>&1 &
                            '
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Deployment successful!'
        }
        failure {
            echo '❌ Deployment failed. Check the logs for more details.'
        }
    }
}