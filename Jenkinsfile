pipeline {
    agent any

    environment {
        EC2_INSTANCE_IP = '13.203.132.105'
        EC2_INSTANCE_USER = 'ec2-user'
        JAR_NAME = 'gems-of-jaipur.jar'
        DEPLOY_PATH = '/home/ec2-user'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            tools {
                maven 'maven' // This matches the name you configured in Jenkins
            }
            steps {
                sh 'mvn clean install -DskipTests=true'
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([file(credentialsId: 'start-gfj-script', variable: 'START_SCRIPT_PATH')]) {
                    sshagent(credentials: ['ec2-creds']) {
                        sh """
                            echo "Copying application files to EC2 instance..."

                            scp target/${JAR_NAME} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/

                            scp ${START_SCRIPT_PATH} ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP}:${DEPLOY_PATH}/start-gfj.sh

                            echo "Connecting to EC2 instance and restarting application..."
                            ssh -o StrictHostKeyChecking=no ${EC2_INSTANCE_USER}@${EC2_INSTANCE_IP} <<EOF
                                echo "Stopping any existing application process..."

                                pkill -f "${JAR_NAME}" || true

                                chmod +x ${DEPLOY_PATH}/start-gfj.sh

                                echo "Starting the new application using the start script..."
                                nohup bash ${DEPLOY_PATH}/start-gfj.sh &
                            EOF
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'Deployment successful!'
        }
        failure {
            echo 'Deployment failed. Check the logs for more details.'
        }
    }
}
