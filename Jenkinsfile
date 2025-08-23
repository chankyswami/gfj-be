pipeline {
    agent any

    environment {
        EC2_INSTANCE_IP = '13.203.132.105'
        EC2_INSTANCE_USER = 'ec2-user'
        JAR_NAME = 'gfj-be-0.0.32-SNAPSHOT.jar'
        DEPLOY_PATH = '/home/ec2-user'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
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
            }
        }

        stage('Deploy') {
            steps {
                configFileProvider([configFile(fileId: 'start-gfj-script', variable: 'START_SCRIPT_PATH')]) {
                    sshagent(credentials: ['ec2-creds']) {
                        sh """
                            echo "🚀 Copying application files to EC2 instance..."

                            scp -o StrictHostKeyChecking=no target/${JAR_NAME} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/

                            scp -o StrictHostKeyChecking=no ${START_SCRIPT_PATH} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/start-gfj.sh

                            echo "🔄 Connecting to EC2 instance and restarting application..."

                            ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} '
                                echo "🛑 Stopping any existing application process..." &&
                                pkill -f "${JAR_NAME}" || true &&
                                chmod +x ${DEPLOY_PATH}/start-gfj.sh &&
                                echo "▶️ Starting the new application using the start script..." &&
                                nohup bash ${DEPLOY_PATH}/start-gfj.sh &
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
