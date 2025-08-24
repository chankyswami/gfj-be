module "vpc" {
  source     = "../../modules/vpc"
  vpc_name   = "gem-jaipur"
  cidr_block = "10.0.0.0/24"
}

module "subnet" {
  source         = "../../modules/subnet"
  vpc_id         = module.vpc.vpc_id
  subnet_cidr    = "10.0.0.0/27"
  availability_zone = "ap-south-1a"
  vpc_name       = "gem-jaipur"
  route_table_id = module.vpc.public_route_table_id
}

module "ec2_1" {
  source        = "../../modules/ec2"
  subnet_id     = module.subnet.subnet_id
  vpc_id        = module.vpc.vpc_id
  ami_id        = var.ami_id_1
  instance_type = "t4g.small"
  name          = "gem-db"
  key_name      = "gfj-dev"
}

module "ec2_2" {
  source        = "../../modules/ec2"
  subnet_id     = module.subnet.subnet_id
  vpc_id        = module.vpc.vpc_id
  ami_id        = var.ami_id_2
  instance_type = "t4g.small"
  name          = "gem-app"
  key_name      = "gfj-dev"
}

module "eip" {
  source = "../../modules/eip"
}

resource "aws_eip_association" "db_assoc" {
  instance_id   = module.ec2_1.instance_id
  allocation_id = module.eip.db_allocation_id
}

resource "aws_eip_association" "app_assoc" {
  instance_id   = module.ec2_2.instance_id
  allocation_id = module.eip.application_allocation_id
}
